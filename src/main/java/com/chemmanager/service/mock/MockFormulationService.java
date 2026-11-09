package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.chemmanager.dto.FormulationComparison;
import com.chemmanager.dto.FormulationQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.Formulation;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;
import com.chemmanager.model.Product;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.FormulationService;
import com.chemmanager.util.Paging;

/**
 * Formulations backed by the demo dataset.
 *
 * <p>The rules are the same as the API: an approved version is locked, a change creates a new
 * version, the cost is the sum of the quantities at the current purchase prices and an empty
 * version cannot be approved. Demo mode therefore exercises the same code paths as API mode.</p>
 */
public final class MockFormulationService implements FormulationService {

    private final MockDataStore store = MockDataStore.get();
    private final List<FormulationState> states = new ArrayList<>();
    private long nextFormulationId;
    private long nextVersionId;

    public MockFormulationService() {
        seed();
    }

    @Override
    public PagedResult<Formulation> search(FormulationQuery query) {
        FormulationQuery effective = query == null ? FormulationQuery.initial(20) : query;
        String needle = normalize(effective.search());
        List<Formulation> rows = new ArrayList<>();
        for (FormulationState state : states) {
            Formulation summary = state.summary();
            if (effective.status() != null && !effective.status().isBlank()
                    && !effective.status().equalsIgnoreCase(summary.status())) {
                continue;
            }
            if (!needle.isEmpty() && !matches(summary, needle)) {
                continue;
            }
            rows.add(summary);
        }
        rows.sort(Comparator.comparing(Formulation::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return Paging.slice(rows, effective.page(), effective.pageSize());
    }

    @Override
    public Formulation load(long id) {
        return state(id).summary();
    }

    @Override
    public List<FormulationVersion> versions(long formulationId) {
        List<FormulationVersion> versions = new ArrayList<>(state(formulationId).versions);
        versions.sort(Comparator.comparingInt(FormulationVersion::version).reversed());
        return versions;
    }

    @Override
    public FormulationVersion version(long formulationId, long versionId) {
        return state(formulationId).versions.stream()
                .filter(version -> version.id() == versionId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown formulation version " + versionId));
    }

    @Override
    public Formulation create(String nameFr, String nameAr, Long productId, String notes) {
        String code = String.format("FML-%04d", nextFormulationId + 1);
        FormulationState state = new FormulationState(nextFormulationId++, code, nameFr,
                nameAr == null || nameAr.isBlank() ? nameFr : nameAr, productId, notes);
        states.add(state);
        return state.summary();
    }

    @Override
    public FormulationVersion createVersion(long formulationId) {
        FormulationState state = state(formulationId);
        FormulationVersion previous = state.current();
        FormulationVersion created = new FormulationVersion(
                nextVersionId++,
                state.id,
                state.versions.size() + 1,
                previous == null ? "" : previous.instructions(),
                previous == null ? BigDecimal.ZERO : previous.expectedYield(),
                previous == null ? "KG" : previous.yieldUnit(),
                false, null, null,
                BigDecimal.ZERO, 0,
                previous == null ? List.of() : previous.items());
        state.versions.add(created);
        state.touch();
        return created;
    }

    @Override
    public FormulationVersion updateVersion(long formulationId, long versionId, String instructions,
                                            BigDecimal expectedYield, String yieldUnit,
                                            List<FormulationItem> items) {
        FormulationState state = state(formulationId);
        FormulationVersion current = version(formulationId, versionId);
        if (current.locked()) {
            throw new IllegalStateException("FORMULATION_LOCKED");
        }
        List<FormulationItem> costed = cost(items);
        FormulationVersion updated = new FormulationVersion(current.id(), current.formulationId(),
                current.version(), instructions == null ? current.instructions() : instructions,
                expectedYield == null ? current.expectedYield() : expectedYield,
                yieldUnit == null ? current.yieldUnit() : yieldUnit,
                false, null, null, total(costed), costed.size(), costed);
        state.versions.replaceAll(version -> version.id() == versionId ? updated : version);
        state.touch();
        return updated;
    }

    @Override
    public FormulationVersion approve(long formulationId, long versionId) {
        FormulationState state = state(formulationId);
        FormulationVersion current = version(formulationId, versionId);
        if (current.locked()) {
            throw new IllegalStateException("FORMULATION_LOCKED");
        }
        if (current.items().isEmpty()) {
            throw new IllegalArgumentException("A formulation cannot be approved without raw materials.");
        }
        FormulationVersion approved = new FormulationVersion(current.id(), current.formulationId(),
                current.version(), current.instructions(), current.expectedYield(), current.yieldUnit(),
                true, "admin", LocalDateTime.now(), current.cost(), current.itemCount(), current.items());
        state.versions.replaceAll(version -> version.id() == versionId ? approved : version);
        state.status = "APPROVED";
        state.currentVersionId = approved.id();
        state.approvedAt = approved.approvedAt();
        state.approvedByName = approved.approvedByName();
        state.touch();
        return approved;
    }

    @Override
    public Formulation archive(long formulationId) {
        FormulationState state = state(formulationId);
        state.status = "ARCHIVED";
        state.touch();
        return state.summary();
    }

    @Override
    public FormulationComparison compare(long formulationId, long leftVersionId, long rightVersionId) {
        FormulationVersion left = version(formulationId, leftVersionId);
        FormulationVersion right = version(formulationId, rightVersionId);
        Map<Long, FormulationItem> leftItems = index(left.items());
        Map<Long, FormulationItem> rightItems = index(right.items());

        List<FormulationComparison.Line> lines = new ArrayList<>();
        for (Map.Entry<Long, FormulationItem> entry : leftItems.entrySet()) {
            FormulationItem other = rightItems.get(entry.getKey());
            BigDecimal leftQuantity = entry.getValue().quantity();
            BigDecimal rightQuantity = other == null ? BigDecimal.ZERO : other.quantity();
            BigDecimal delta = rightQuantity.subtract(leftQuantity);
            String status = other == null ? "REMOVED"
                    : delta.abs().compareTo(new BigDecimal("0.00005")) > 0 ? "CHANGED" : "UNCHANGED";
            lines.add(new FormulationComparison.Line(entry.getValue().materialCode(),
                    entry.getValue().materialName(), entry.getValue().unit(), leftQuantity, rightQuantity,
                    delta, status));
        }
        for (Map.Entry<Long, FormulationItem> entry : rightItems.entrySet()) {
            if (!leftItems.containsKey(entry.getKey())) {
                lines.add(new FormulationComparison.Line(entry.getValue().materialCode(),
                        entry.getValue().materialName(), entry.getValue().unit(), BigDecimal.ZERO,
                        entry.getValue().quantity(), entry.getValue().quantity(), "ADDED"));
            }
        }

        return new FormulationComparison(left.version(), right.version(), left.cost(), right.cost(),
                right.cost().subtract(left.cost()), lines);
    }

    // ------------------------------------------------------------------ internals

    private void seed() {
        List<Product> products = store.products();
        List<RawMaterial> materials = store.rawMaterials();
        if (products.isEmpty() || materials.size() < 3) {
            return;
        }
        String[][] seeds = {
                {"Gel douche surgras 1 L", "جل استحمام فائق النعومة 1 ل", "APPROVED"},
                {"Détergent liquide lessive 3 L", "منظف سائل للغسيل 3 ل", "APPROVED"},
                {"Nettoyant sol parfumé 5 L", "منظف الأرضيات معطر 5 ل", "DRAFT"},
        };
        for (int index = 0; index < seeds.length; index++) {
            Product product = products.get(index % products.size());
            FormulationState state = new FormulationState(nextFormulationId++,
                    String.format("FML-%04d", index + 1), seeds[index][0], seeds[index][1],
                    product.id(), "Formule saisie et validée par le responsable de production.");
            List<FormulationItem> items = new ArrayList<>();
            for (int line = 0; line < 3; line++) {
                RawMaterial material = materials.get((index * 3 + line) % materials.size());
                BigDecimal quantity = BigDecimal.valueOf(30 - line * 8).setScale(3, RoundingMode.HALF_UP);
                items.add(new FormulationItem(material.id(), material.code(), material.commercialName(),
                        quantity, material.storageUnit(), BigDecimal.ZERO, line == 0 ? "A" : "B",
                        BigDecimal.ZERO, material.stock(), material.hazardClass()));
            }
            List<FormulationItem> costed = cost(items);
            boolean approved = "APPROVED".equals(seeds[index][2]);
            FormulationVersion version = new FormulationVersion(nextVersionId++, state.id, 1,
                    "Phase A : disperser les tensioactifs. Phase B : ajouter le parfum et le conservateur.",
                    BigDecimal.valueOf(100), "KG", approved, approved ? "admin" : null,
                    approved ? LocalDateTime.now().minusDays(12) : null,
                    total(costed), costed.size(), costed);
            state.versions.add(version);
            if (approved) {
                state.status = "APPROVED";
                state.currentVersionId = version.id();
                state.approvedAt = version.approvedAt();
                state.approvedByName = "admin";
            }
            state.touch();
            states.add(state);
        }
    }

    private List<FormulationItem> cost(List<FormulationItem> items) {
        Map<Long, BigDecimal> prices = new LinkedHashMap<>();
        for (RawMaterial material : store.rawMaterials()) {
            prices.put(material.id(), material.purchasePrice());
        }
        BigDecimal total = BigDecimal.ZERO;
        List<FormulationItem> costed = new ArrayList<>();
        for (FormulationItem item : items) {
            BigDecimal price = prices.getOrDefault(item.materialId(), BigDecimal.ZERO);
            BigDecimal line = item.quantity().multiply(price).setScale(2, RoundingMode.HALF_UP);
            costed.add(new FormulationItem(item.materialId(), item.materialCode(), item.materialName(),
                    item.quantity(), item.unit(), item.percentage(), item.phase(), line,
                    item.stockQuantity(), item.hazardClass()));
            total = total.add(item.quantity());
        }
        if (total.signum() > 0) {
            BigDecimal sum = total;
            costed.replaceAll(item -> new FormulationItem(item.materialId(), item.materialCode(),
                    item.materialName(), item.quantity(), item.unit(),
                    item.quantity().multiply(BigDecimal.valueOf(100))
                            .divide(sum, 4, RoundingMode.HALF_UP),
                    item.phase(), item.lineCost(), item.stockQuantity(), item.hazardClass()));
        }
        return costed;
    }

    private static BigDecimal total(List<FormulationItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FormulationItem item : items) {
            sum = sum.add(item.lineCost());
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static Map<Long, FormulationItem> index(List<FormulationItem> items) {
        Map<Long, FormulationItem> map = new LinkedHashMap<>();
        for (FormulationItem item : items) {
            map.put(item.materialId(), item);
        }
        return map;
    }

    private FormulationState state(long id) {
        return states.stream().filter(state -> state.id == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown formulation " + id));
    }

    private static boolean matches(Formulation formulation, String needle) {
        return normalize(formulation.code()).contains(needle)
                || normalize(formulation.nameFr()).contains(needle)
                || normalize(formulation.nameAr()).contains(needle)
                || normalize(formulation.productName()).contains(needle);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    /** Mutable demo record: the versions list and the approval state of one family. */
    private static final class FormulationState {
        private final long id;
        private final String code;
        private final String nameFr;
        private final String nameAr;
        private final Long productId;
        private final String notes;
        private final List<FormulationVersion> versions = new ArrayList<>();
        private String status = "DRAFT";
        private Long currentVersionId;
        private LocalDateTime approvedAt;
        private String approvedByName;
        private LocalDateTime updatedAt = LocalDateTime.now().minusDays(20);

        FormulationState(long id, String code, String nameFr, String nameAr, Long productId, String notes) {
            this.id = id;
            this.code = code;
            this.nameFr = nameFr;
            this.nameAr = nameAr;
            this.productId = productId;
            this.notes = notes;
        }

        void touch() {
            updatedAt = LocalDateTime.now();
        }

        FormulationVersion current() {
            FormulationVersion newest = null;
            for (FormulationVersion version : versions) {
                if (newest == null || version.version() > newest.version()) {
                    newest = version;
                }
            }
            return newest;
        }

        Formulation summary() {
            FormulationVersion current = current();
            String productName = MockDataStore.get().products().stream()
                    .filter(product -> productId != null && product.id() == productId)
                    .map(Product::nameFr)
                    .findFirst()
                    .orElse(null);
            return new Formulation(id, code, nameFr, nameAr, productId, productName, status,
                    currentVersionId, current == null ? null : current.version(),
                    current == null ? BigDecimal.ZERO : current.cost(),
                    current == null ? BigDecimal.ZERO : current.expectedYield(),
                    current == null ? "KG" : current.yieldUnit(),
                    versions.size(), approvedByName, notes, updatedAt);
        }
    }
}
