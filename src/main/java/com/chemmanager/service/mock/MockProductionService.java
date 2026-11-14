package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductionQuery;
import com.chemmanager.model.production.ApprovedVersion;
import com.chemmanager.model.production.ProductionGenealogy;
import com.chemmanager.model.production.ProductionOrder;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.ProductionService;
import com.chemmanager.util.Paging;

/**
 * Production backed by the demo dataset.
 *
 * <p>The demo keeps three orders on purpose — one waiting for material, one in progress and one
 * released — so the screen shows the three states a production manager actually works with. Nothing
 * here is a business rule: the real server refuses what this demo can only illustrate.</p>
 */
public final class MockProductionService implements ProductionService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final List<ProductionOrder> orders = new ArrayList<>();

    public MockProductionService() {
        MockDataStore store = MockDataStore.get();
        long productId = store.products().isEmpty() ? 1 : store.products().get(0).id();
        String productCode = store.products().isEmpty() ? "PF-0001" : store.products().get(0).sku();
        String productName = store.products().isEmpty() ? "Gel douche" : store.products().get(0).nameFr();
        long warehouseId = 1;
        String warehouseName = store.batches().isEmpty() ? "Magasin" : store.batches().get(0).warehouse();

        orders.add(build(1, "PRD-00001", 4, "F-0004", productId, productCode, productName, warehouseId,
                warehouseName, "WAITING_MATERIALS", LocalDate.now().plusDays(2), bd("500"), bd("0"), bd("0"),
                List.of(line(1, 101, "BT-2026-014", "MP-0001", "Tensioactif anionique LABSA 96%", bd("300"),
                        bd("0"), "KG", bd("245.00")),
                        line(2, 102, "BT-2026-015", "MP-0003", "Soude caustique perles 99%", bd("200"), bd("0"),
                                "KG", bd("180.00"))), List.of(), List.of()));
        orders.add(build(2, "PRD-00002", 5, "F-0004", productId, productCode, productName, warehouseId,
                warehouseName, "IN_PROGRESS", LocalDate.now(), bd("1000"), bd("0"), bd("0"),
                List.of(line(3, 103, "BT-2026-016", "MP-0001", "Tensioactif anionique LABSA 96%", bd("600"),
                        bd("420"), "KG", bd("245.00")),
                        line(4, 104, "BT-2026-017", "MP-0002", "Tensioactif non ionique Alcacare 7EO", bd("400"),
                                bd("0"), "KG", bd("310.00"))), List.of(), List.of()));
        orders.add(build(3, "PRD-00003", 3, "F-0002", productId, productCode, productName, warehouseId,
                warehouseName, "COMPLETED", LocalDate.now().minusDays(3), bd("800"), bd("792"), bd("8"),
                List.of(line(5, 105, "BT-2026-009", "MP-0001", "Tensioactif anionique LABSA 96%", bd("480"),
                        bd("486"), "KG", bd("245.00")),
                        line(6, 106, "BT-2026-010", "MP-0002", "Tensioactif non ionique Alcacare 7EO", bd("320"),
                                bd("318"), "KG", bd("310.00"))),
                List.of(new ProductionOrder.Output(9001, "PF-PRD-00003", bd("792"), "L", "RELEASED",
                        warehouseName, LocalDate.now().minusDays(3), LocalDate.now().plusMonths(18), bd("271.40"),
                        true)),
                List.of(new ProductionOrder.QualityCheck(7001, "aspect", "PASS",
                        LocalDate.now().minusDays(3).toString(), "Conforme", "admin"))));
    }

    @Override
    public List<ApprovedVersion> versions() {
        return List.of(
                new ApprovedVersion(4, 4, "F-0001", "Gel douche hydratant", "جل استحمام مرطب", 1,
                        "Gel douche 750 ml", bd("1000"), "L", 6),
                new ApprovedVersion(5, 5, "F-0004", "Détergent liquide concentré", "منظف سائل مركز", 2,
                        "Détergent 5 L", bd("1000"), "L", 5),
                new ApprovedVersion(6, 2, "F-0002", "Shampooing doux", "شامبو لطيف", 3,
                        "Shampooing 500 ml", bd("800"), "L", 7));
    }

    @Override
    public PagedResult<ProductionOrder> search(ProductionQuery query) {
        ProductionQuery effective = query == null ? ProductionQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().trim().toLowerCase(Locale.ROOT);
        List<ProductionOrder> filtered = orders.stream()
                .filter(order -> effective.status().isEmpty() || effective.status().equals(order.status()))
                .filter(order -> search.isEmpty()
                        || order.orderNumber().toLowerCase(Locale.ROOT).contains(search)
                        || order.productName().toLowerCase(Locale.ROOT).contains(search))
                .sorted(Comparator.comparing(ProductionOrder::plannedDate).reversed())
                .toList();

        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public ProductionOrder load(long id) {
        return orders.stream().filter(order -> order.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Production order not found."));
    }

    @Override
    public ProductionOrder create(ProductionOrder.NewOrder request) {
        ApprovedVersion version = versions().stream()
                .filter(candidate -> candidate.versionId() == request.formulationVersionId())
                .findFirst().orElseThrow(() -> new IllegalStateException("Approved version not found."));
        long id = orders.size() + 1L;
        ProductionOrder created = build(id, String.format("PRD-%05d", id), version.version(),
                version.formulationCode(), version.productId(), "PF-0001", version.productName(),
                request.warehouseId(), "Magasin matières", "DRAFT",
                request.plannedDate() == null ? LocalDate.now() : request.plannedDate(),
                request.plannedQuantity(), bd("0"), bd("0"), List.of(), List.of(), List.of());
        orders.add(0, created);

        return created;
    }

    @Override
    public ProductionOrder start(long id) {
        return replace(id, order -> withStatus(order, "IN_PROGRESS"));
    }

    @Override
    public ProductionOrder consume(long id, List<ProductionOrder.Consumption> items) {
        return replace(id, order -> {
            List<ProductionOrder.MaterialLine> lines = new ArrayList<>();
            for (ProductionOrder.MaterialLine line : order.materials()) {
                BigDecimal consumed = line.consumedQuantity();
                for (ProductionOrder.Consumption item : items) {
                    if (item.materialBatchId() == line.materialBatchId()) {
                        consumed = consumed.add(item.quantity());
                    }
                }
                lines.add(new ProductionOrder.MaterialLine(line.id(), line.materialBatchId(), line.batchNumber(),
                        line.materialCode(), line.materialName(), line.plannedQuantity(), consumed, line.unit(),
                        line.unitPrice(), consumed.multiply(line.unitPrice()).setScale(2, RoundingMode.HALF_UP),
                        consumed.subtract(line.plannedQuantity()), line.expiryDate(), line.qualityStatus()));
            }
            return copy(order, order.status(), order.actualQuantity(), order.wasteQuantity(),
                    cost(lines), order.packagingCost(), order.otherCost(), lines, order.outputs(),
                    order.qualityChecks());
        });
    }

    @Override
    public ProductionOrder complete(long id, ProductionOrder.Completion completion) {
        return replace(id, order -> {
            BigDecimal total = cost(order.materials()).add(completion.packagingCost()).add(completion.otherCost());
            BigDecimal actual = completion.actualQuantity();
            ProductionOrder.Output output = new ProductionOrder.Output(order.id() * 1000L,
                    "PF-" + order.orderNumber(), actual, order.unit(), "QUARANTINE", order.warehouseName(),
                    LocalDate.now(), completion.expiryDate(),
                    actual.signum() == 0 ? BigDecimal.ZERO
                            : total.divide(actual, 2, RoundingMode.HALF_UP), false);
            return copy(order, "QUALITY_CONTROL", actual, completion.wasteQuantity(), cost(order.materials()),
                    completion.packagingCost(), completion.otherCost(), order.materials(), List.of(output),
                    order.qualityChecks());
        });
    }

    @Override
    public ProductionOrder quality(long id, ProductionOrder.QualityDecision decision) {
        return replace(id, order -> {
            boolean pass = "PASS".equalsIgnoreCase(decision.result());
            List<ProductionOrder.Output> outputs = new ArrayList<>();
            for (ProductionOrder.Output output : order.outputs()) {
                outputs.add(new ProductionOrder.Output(output.productBatchId(), output.batchNumber(),
                        output.quantity(), output.unit(), pass ? "RELEASED" : "REJECTED", output.warehouseName(),
                        output.productionDate(), output.expiryDate(), output.unitCost(), pass));
            }
            List<ProductionOrder.QualityCheck> checks = new ArrayList<>(order.qualityChecks());
            checks.add(new ProductionOrder.QualityCheck(checks.size() + 1L, decision.checkType(),
                    pass ? "PASS" : "FAIL", LocalDate.now().toString(), decision.remarks(), "demo"));
            return copy(order, pass ? "COMPLETED" : "REJECTED", order.actualQuantity(), order.wasteQuantity(),
                    order.materialCost(), order.packagingCost(), order.otherCost(), order.materials(), outputs,
                    checks);
        });
    }

    @Override
    public ProductionOrder cancel(long id, String reason) {
        return replace(id, order -> copy(order, "CANCELLED", order.actualQuantity(), order.wasteQuantity(),
                order.materialCost(), order.packagingCost(), order.otherCost(), order.materials(), order.outputs(),
                order.qualityChecks()));
    }

    @Override
    public ProductionGenealogy genealogy(long id) {
        ProductionOrder order = load(id);
        List<ProductionGenealogy.Input> inputs = new ArrayList<>();
        for (ProductionOrder.MaterialLine line : order.materials()) {
            inputs.add(new ProductionGenealogy.Input(line.materialCode(), line.materialName(), line.batchNumber(),
                    line.plannedQuantity(), line.consumedQuantity(), line.unit(), "Sotrachem Import",
                    "BR-0001" + line.id(), "BC-0001", line.expiryDate() == null ? "" : line.expiryDate().toString()));
        }

        return new ProductionGenealogy(order.id(), order.orderNumber(), order.productName(),
                order.formulationCode(), order.formulationVersion(), order.status(),
                order.plannedDate() == null ? "" : order.plannedDate().toString(),
                !inputs.isEmpty() && !order.outputs().isEmpty(), inputs, order.outputs(), order.qualityChecks());
    }

    // ------------------------------------------------------------------ internals

    private ProductionOrder replace(long id, java.util.function.UnaryOperator<ProductionOrder> update) {
        for (int index = 0; index < orders.size(); index++) {
            ProductionOrder current = orders.get(index);
            if (current.id() == id) {
                ProductionOrder updated = update.apply(current);
                orders.set(index, updated);
                return updated;
            }
        }
        throw new IllegalStateException("Production order not found.");
    }

    private static ProductionOrder withStatus(ProductionOrder order, String status) {
        return copy(order, status, order.actualQuantity(), order.wasteQuantity(), order.materialCost(),
                order.packagingCost(), order.otherCost(), order.materials(), order.outputs(),
                order.qualityChecks());
    }

    private static BigDecimal cost(List<ProductionOrder.MaterialLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (ProductionOrder.MaterialLine line : lines) {
            total = total.add(line.consumedQuantity().multiply(line.unitPrice()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static ProductionOrder build(long id, String number, int version, String formulationCode,
                                         long productId, String productCode, String productName, long warehouseId,
                                         String warehouseName, String status, LocalDate date, BigDecimal planned,
                                         BigDecimal actual, BigDecimal waste,
                                         List<ProductionOrder.MaterialLine> materials,
                                         List<ProductionOrder.Output> outputs,
                                         List<ProductionOrder.QualityCheck> checks) {
        return copy(new ProductionOrder(id, number, version, formulationCode, productId, productCode, productName,
                warehouseId, warehouseName, planned, actual, waste, "L", status, date, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, outputs.stream().anyMatch(ProductionOrder.Output::sellable),
                "", List.of(), materials, outputs, checks), status, actual, waste, cost(materials),
                BigDecimal.ZERO, BigDecimal.ZERO, materials, outputs, checks);
    }

    private static ProductionOrder copy(ProductionOrder order, String status, BigDecimal actual, BigDecimal waste,
                                        BigDecimal materialCost, BigDecimal packaging, BigDecimal other,
                                        List<ProductionOrder.MaterialLine> materials,
                                        List<ProductionOrder.Output> outputs,
                                        List<ProductionOrder.QualityCheck> checks) {
        BigDecimal total = materialCost.add(packaging).add(other).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costPerUnit = actual.signum() == 0 ? BigDecimal.ZERO
                : total.divide(actual, 2, RoundingMode.HALF_UP);
        BigDecimal standard = BigDecimal.ZERO;
        for (ProductionOrder.MaterialLine line : materials) {
            standard = standard.add(line.plannedQuantity().multiply(line.unitPrice()));
        }
        standard = standard.setScale(2, RoundingMode.HALF_UP);
        BigDecimal variance = materialCost.subtract(standard);
        BigDecimal variancePct = standard.signum() == 0 ? BigDecimal.ZERO
                : variance.multiply(HUNDRED).divide(standard, 2, RoundingMode.HALF_UP);
        BigDecimal consumed = BigDecimal.ZERO;
        for (ProductionOrder.MaterialLine line : materials) {
            consumed = consumed.add(line.consumedQuantity());
        }
        boolean sellable = !outputs.isEmpty() && outputs.stream().allMatch(ProductionOrder.Output::sellable);

        return new ProductionOrder(order.id(), order.orderNumber(), order.formulationVersion(),
                order.formulationCode(), order.productId(), order.productCode(), order.productName(),
                order.warehouseId(), order.warehouseName(), order.plannedQuantity(), actual, waste, order.unit(),
                status, order.plannedDate(), materialCost, packaging, other, total, costPerUnit, standard, variance,
                variancePct, consumed, sellable, order.notes(), order.plan(), materials, outputs, checks);
    }

    private static ProductionOrder.MaterialLine line(long id, long batchId, String batchNumber, String code,
                                                     String name, BigDecimal planned, BigDecimal consumed,
                                                     String unit, BigDecimal price) {
        return new ProductionOrder.MaterialLine(id, batchId, batchNumber, code, name, planned, consumed, unit,
                price, consumed.multiply(price).setScale(2, RoundingMode.HALF_UP),
                consumed.subtract(planned), null, "RELEASED");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
