package com.chemmanager.service.mock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.safety.HazardPictogram;
import com.chemmanager.model.safety.IncidentSummary;
import com.chemmanager.model.safety.InstructionUpdate;
import com.chemmanager.model.safety.MaterialFile;
import com.chemmanager.model.safety.MaterialHazard;
import com.chemmanager.model.safety.NewIncident;
import com.chemmanager.model.safety.NewSheet;
import com.chemmanager.model.safety.SafetyIncident;
import com.chemmanager.model.safety.SafetyInstructions;
import com.chemmanager.model.safety.SafetyOverview;
import com.chemmanager.model.safety.SafetySheet;
import com.chemmanager.service.SafetyService;

/**
 * Offline demo of the chemical register.
 *
 * <p>The rules are replayed in memory exactly as the server enforces them: a sheet older than three
 * years is obsolete, registering a current sheet retires the previous one, and an incident walks
 * OPEN → INVESTIGATING → CLOSED without ever going back.</p>
 */
public final class MockSafetyService implements SafetyService {

    /** A sheet older than this many years is obsolete, whatever its revision. */
    public static final int SHEET_VALIDITY_YEARS = 3;

    private static final List<String> DOCUMENT_TYPES = List.of("SDS", "TECHNICAL", "DISPOSAL", "TRANSPORT");

    private static final List<String> INCIDENT_TYPES = List.of("SPILL", "EXPOSURE", "FIRE", "DAMAGE", "OTHER");

    private static final List<String> SEVERITIES = List.of("MINOR", "MODERATE", "MAJOR", "CRITICAL");

    private static final List<String> STATUSES = List.of("OPEN", "INVESTIGATING", "CLOSED");

    private final List<HazardPictogram> pictograms = new ArrayList<>();
    private final List<MaterialHazard> materials = new ArrayList<>();
    private final List<SafetySheet> sheets = new ArrayList<>();
    private final List<SafetyInstructions> instructions = new ArrayList<>();
    private final List<String[]> materialPictograms = new ArrayList<>();
    private final List<SafetyIncident> incidents = new ArrayList<>();

    private long nextSheetId = 100;
    private long nextIncidentId = 3;

    public MockSafetyService() {
        seed();
    }

    // ------------------------------------------------------------------ seed

    private void seed() {
        pictograms.add(new HazardPictogram(1, "GHS01", "Explosif", "متفجر", "Explosive", "", 0));
        pictograms.add(new HazardPictogram(2, "GHS02", "Inflammable", "قابل للاشتعال", "Flammable", "", 0));
        pictograms.add(new HazardPictogram(3, "GHS03", "Comburant", "مؤكسد", "Oxidising", "", 0));
        pictograms.add(new HazardPictogram(4, "GHS04", "Gaz sous pression", "غاز تحت الضغط", "Gas under pressure",
                "", 0));
        pictograms.add(new HazardPictogram(5, "GHS05", "Corrosif", "مادة أكالة", "Corrosive", "", 0));
        pictograms.add(new HazardPictogram(6, "GHS06", "Toxique", "سام", "Toxic", "", 0));
        pictograms.add(new HazardPictogram(7, "GHS07", "Irritant", "مهيج", "Irritant", "", 0));
        pictograms.add(new HazardPictogram(8, "GHS08", "Danger pour la santé", "خطر على الصحة", "Health hazard",
                "", 0));
        pictograms.add(new HazardPictogram(9, "GHS09", "Danger pour l'environnement", "خطر على البيئة",
                "Environmental hazard", "", 0));

        material("MP-0001", "Savon de Marseille (base)", "Savon de sodium", "GHS07", "Lieu sec",
                "Magasin matières - R1");
        material("MP-0002", "Soude caustique liquide 30%", "Hydroxyde de sodium", "GHS05", "Fût fermé",
                "Magasin matières - R1");
        material("MP-0003", "Acide chlorhydrique 33%", "Acide chlorhydrique", "GHS05", "Local ventilé",
                "Magasin matières - R2");
        material("MP-0004", "Alcool cétylique", "Alcohol C16", "GHS05,GHS09", "Lieu frais",
                "Magasin matières - R2");
        material("MP-0005", "Parfum fleur d'oranger", "Mélange parfumé", "GHS02", "Loin des flammes",
                "Magasin matières - R3");
        material("MP-0006", "Glycérine végétale", "Glycérol", "", "Lieu sec", "Magasin matières - R2");
        material("MP-0007", "Soude caustique en paillettes", "Hydroxyde de sodium solide", "GHS05",
                "Sac fermé", "Magasin matières - R1");
        material("MP-0008", "Acide citrique", "Acide 2-hydroxypropane-1,2,3-tricarboxylique", "GHS07",
                "Lieu sec", "Magasin matières - R2");
        material("MP-0009", "Alcool isopropylique", "Propan-2-ol", "GHS02,GHS07", "Armoire anti-feu",
                "Magasin matières - R3");
        material("MP-0010", "Colorant jaune E102", "Tartrazine", "GHS07", "Lieu sec",
                "Magasin matières - R3");
        material("MP-0011", "Huile d'olive vierge", "Huile végétale", "", "Lieu frais",
                "Magasin matières - R4");
        material("MP-0012", "Eau déminéralisée", "Eau purifiée", "", "Lieu sec", "Magasin matières - R4");

        sheets.add(new SafetySheet(1, 1, "MP-0001", "Savon de Marseille (base)", "SDS",
                "sds/MP-0001-REV-4.pdf", LocalDate.now().minusYears(1).withDayOfMonth(14), "REV-4",
                "Fournisseur - Chimie Sétif", true, false, LocalDateTime.now().minusMonths(9)));
        sheets.add(new SafetySheet(2, 1, "MP-0001", "Savon de Marseille (base)", "SDS",
                "sds/MP-0001-REV-3.pdf", LocalDate.now().minusYears(5), "REV-3", "Fournisseur - Chimie Sétif",
                false, true, LocalDateTime.now().minusYears(4)));
        sheets.add(new SafetySheet(3, 2, "MP-0002", "Soude caustique liquide 30%", "SDS",
                "sds/MP-0002-REV-2.pdf", LocalDate.now().minusYears(2), "REV-2", "Fournisseur - Chimie Sétif",
                true, false, LocalDateTime.now().minusMonths(20)));
        sheets.add(new SafetySheet(4, 3, "MP-0003", "Acide chlorhydrique 33%", "SDS",
                "sds/MP-0003-REV-1.pdf", LocalDate.now().minusYears(5).minusMonths(2), "REV-1",
                "Fournisseur - Chimie Sétif", true, true, LocalDateTime.now().minusYears(5)));

        instructions.add(new SafetyInstructions(1L, 1, "Gants nitrile, lunettes", "Lieu sec",
                "Éviter la poussière", "Rincer à l'eau", "Poudre ABC", "Balayer et collecter"));
        instructions.add(new SafetyInstructions(2L, 2, "Gants nitrile, visière, tablier", "Fût fermé, local ventilé",
                "Verser lentement dans l'eau", "Rincer 15 minutes", "Poudre ABC", "Absorber avec de la vermiculite"));

        materialPictograms.add(new String[]{"MP-0001", "GHS07"});
        materialPictograms.add(new String[]{"MP-0002", "GHS05"});
        materialPictograms.add(new String[]{"MP-0003", "GHS05"});
        materialPictograms.add(new String[]{"MP-0004", "GHS05", "GHS09"});
        materialPictograms.add(new String[]{"MP-0005", "GHS02"});
        materialPictograms.add(new String[]{"MP-0007", "GHS05"});
        materialPictograms.add(new String[]{"MP-0008", "GHS07"});
        materialPictograms.add(new String[]{"MP-0009", "GHS02", "GHS07"});
        materialPictograms.add(new String[]{"MP-0010", "GHS07"});

        incidents.add(new SafetyIncident(1, "INC-00001", 2L, "Soude caustique liquide 30%",
                LocalDateTime.now().minusDays(9).withHour(10).withMinute(30), "SPILL", "MINOR",
                "Fuite d'un fût de soude lors du transvasement", "Zone rincée, fût remplacé", "CLOSED",
                LocalDateTime.now().minusDays(8), "Karim Belhadj"));
        incidents.add(new SafetyIncident(2, "INC-00002", 3L, "Acide chlorhydrique 33%",
                LocalDateTime.now().minusDays(4).withHour(14).withMinute(10), "EXPOSURE", "MODERATE",
                "Projection sur l'avant-bras lors du prélèvement", "Rinçage 15 minutes, visite médicale",
                "OPEN", null, "Nadia Cherif"));
    }

    private void material(String code, String commercialName, String chemicalName, String hazard,
                          String storage, String location) {
        long id = materials.size() + 1;
        materials.add(new MaterialHazard(id, code, commercialName, chemicalName, hazard.equals("") ? "" : hazard,
                hazard, storage, location, !hazard.isEmpty(), false, null, null, null, false, 0, 0, 0));
    }

    // ------------------------------------------------------------------ register

    @Override
    public SafetyOverview overview() {
        refresh();
        int hazardous = (int) materials.stream().filter(MaterialHazard::isHazardous).count();
        int withSheet = (int) materials.stream().filter(MaterialHazard::hasSheet).count();
        int current = (int) sheets.stream().filter(sheet -> sheet.isCurrent()
                && "SDS".equals(sheet.documentType())).count();
        int obsolete = (int) sheets.stream().filter(sheet -> sheet.isCurrent()
                && "SDS".equals(sheet.documentType()) && sheet.isObsolete()).count();

        return new SafetyOverview(materials.size(), hazardous, withSheet, Math.max(0, hazardous - withSheet),
                current, obsolete, instructions.size(), incidents.size(), openIncidents(),
                (int) incidents.stream().filter(SafetyIncident::isSevere).count(), SHEET_VALIDITY_YEARS);
    }

    @Override
    public List<HazardPictogram> pictograms() {
        List<HazardPictogram> copy = new ArrayList<>();
        for (HazardPictogram pictogram : pictograms) {
            int count = (int) materialPictograms.stream()
                    .filter(row -> java.util.Arrays.stream(row, 1, row.length)
                            .anyMatch(pictogram.code()::equals))
                    .count();
            copy.add(new HazardPictogram(pictogram.id(), pictogram.code(), pictogram.nameFr(),
                    pictogram.nameAr(), pictogram.nameEn(), pictogram.description(), count));
        }

        return copy;
    }

    @Override
    public PagedResult<MaterialHazard> materials(int page, int pageSize, String search, String risk) {
        refresh();
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<MaterialHazard> rows = materials.stream()
                .filter(row -> needle.isEmpty()
                        || row.code().toLowerCase(Locale.ROOT).contains(needle)
                        || row.commercialName().toLowerCase(Locale.ROOT).contains(needle)
                        || row.chemicalName().toLowerCase(Locale.ROOT).contains(needle)
                        || row.hazardClass().toLowerCase(Locale.ROOT).contains(needle))
                .filter(row -> switch (risk == null ? "" : risk) {
                    case "hazardous" -> row.isHazardous();
                    case "missing" -> row.isUndocumented();
                    case "obsolete" -> row.isHazardous() && row.sheetObsolete();
                    default -> true;
                })
                .sorted(Comparator.comparing(MaterialHazard::isHazardous).reversed()
                        .thenComparing(MaterialHazard::commercialName))
                .toList();

        return page(rows, page, pageSize);
    }

    @Override
    public MaterialFile material(long id) {
        MaterialHazard material = materials.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Matériau introuvable: " + id));
        refresh();

        return file(material);
    }

    @Override
    public PagedResult<SafetySheet> sheets(int page, int pageSize, String search, String documentType,
                                           boolean currentOnly) {
        refresh();
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String type = documentType == null ? "" : documentType.trim().toUpperCase(Locale.ROOT);
        List<SafetySheet> rows = sheets.stream()
                .filter(sheet -> needle.isEmpty()
                        || sheet.materialCode().toLowerCase(Locale.ROOT).contains(needle)
                        || sheet.materialName().toLowerCase(Locale.ROOT).contains(needle)
                        || sheet.revision().toLowerCase(Locale.ROOT).contains(needle)
                        || sheet.source().toLowerCase(Locale.ROOT).contains(needle))
                .filter(sheet -> type.isEmpty() || sheet.documentType().equals(type))
                .filter(sheet -> !currentOnly || sheet.isCurrent())
                .sorted(Comparator.comparing(SafetySheet::issueDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return page(rows, page, pageSize);
    }

    @Override
    public MaterialFile registerSheet(NewSheet sheet) {
        if (sheet.filePath() == null || sheet.filePath().isBlank()) {
            throw new IllegalArgumentException("Le fichier du document est obligatoire.");
        }
        String type = sheet.documentType() == null || sheet.documentType().isBlank() ? "SDS"
                : sheet.documentType().toUpperCase(Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Type de document inconnu: " + type);
        }
        MaterialHazard material = materials.stream().filter(row -> row.id() == sheet.materialId()).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Matériau introuvable: " + sheet.materialId()));

        for (int index = 0; index < sheets.size(); index++) {
            SafetySheet existing = sheets.get(index);
            if (existing.materialId() == sheet.materialId() && existing.documentType().equals(type)
                    && existing.isCurrent()) {
                sheets.set(index, new SafetySheet(existing.id(), existing.materialId(), existing.materialCode(),
                        existing.materialName(), existing.documentType(), existing.filePath(),
                        existing.issueDate(), existing.revision(), existing.source(), false,
                        existing.isObsolete(), existing.createdAt()));
            }
        }
        SafetySheet registered = new SafetySheet(nextSheetId++, sheet.materialId(), material.code(),
                material.commercialName(), type, sheet.filePath(), sheet.issueDate(),
                sheet.revision() == null ? "" : sheet.revision(), sheet.source() == null ? "" : sheet.source(),
                true, obsolete(sheet.issueDate()), LocalDateTime.now());
        sheets.add(registered);
        refresh();

        return file(material);
    }

    @Override
    public MaterialFile saveInstructions(long materialId, InstructionUpdate update) {
        MaterialHazard material = materials.stream().filter(row -> row.id() == materialId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Matériau introuvable: " + materialId));
        SafetyInstructions saved = new SafetyInstructions((long) instructions.size() + 1, materialId,
                update.ppe(), update.storage(), update.handling(), update.firstAid(), update.fireMeasures(),
                update.spillage());
        instructions.removeIf(row -> row.materialId() == materialId);
        instructions.add(saved);
        refresh();

        return file(material);
    }

    @Override
    public MaterialFile setPictograms(long materialId, List<String> codes) {
        MaterialHazard material = materials.stream().filter(row -> row.id() == materialId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Matériau introuvable: " + materialId));
        List<String> wanted = codes == null ? List.of() : codes.stream().distinct().toList();
        for (String code : wanted) {
            if (pictograms.stream().noneMatch(pictogram -> pictogram.code().equals(code))) {
                throw new IllegalArgumentException("Pictogramme inconnu: " + code);
            }
        }
        materialPictograms.removeIf(codes2 -> codes2[0].equals(material.code()));
        if (!wanted.isEmpty()) {
            String[] row = new String[wanted.size() + 1];
            row[0] = material.code();
            for (int index = 0; index < wanted.size(); index++) {
                row[index + 1] = wanted.get(index);
            }
            materialPictograms.add(row);
        }
        refresh();

        return file(material);
    }

    // ------------------------------------------------------------------ incidents

    @Override
    public PagedResult<SafetyIncident> incidents(int page, int pageSize, String status, String severity,
                                                 String search) {
        List<SafetyIncident> rows = filteredIncidents(status, severity, search);
        return page(rows, page, pageSize);
    }

    @Override
    public IncidentSummary incidentSummary(String status, String severity, String search) {
        List<SafetyIncident> rows = filteredIncidents(status, severity, search);
        return new IncidentSummary(rows.size(), (int) rows.stream().filter(SafetyIncident::isOpen).count(),
                (int) rows.stream().filter(SafetyIncident::isSevere).count());
    }

    @Override
    public SafetyIncident reportIncident(NewIncident incident) {
        if (incident.description() == null || incident.description().isBlank()) {
            throw new IllegalArgumentException("La description de l'incident est obligatoire.");
        }
        String type = incident.incidentType() == null ? "OTHER"
                : incident.incidentType().toUpperCase(Locale.ROOT);
        if (!INCIDENT_TYPES.contains(type)) {
            throw new IllegalArgumentException("Type d'incident inconnu: " + type);
        }
        String severity = incident.severity() == null ? "MINOR"
                : incident.severity().toUpperCase(Locale.ROOT);
        if (!SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("Gravité inconnue: " + severity);
        }
        String materialName = incident.materialId() == null ? ""
                : materials.stream().filter(row -> row.id() == incident.materialId())
                        .map(MaterialHazard::commercialName).findFirst().orElse("");
        SafetyIncident reported = new SafetyIncident(nextIncidentId,
                "INC-" + String.format(Locale.ROOT, "%05d", nextIncidentId), incident.materialId(),
                materialName, LocalDateTime.now(), type, severity, incident.description(),
                incident.actionsTaken() == null ? "" : incident.actionsTaken(), "OPEN", null,
                "Utilisateur de démonstration");
        nextIncidentId++;
        incidents.add(reported);

        return reported;
    }

    @Override
    public SafetyIncident changeIncidentStatus(long id, String status) {
        SafetyIncident current = incidents.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Incident introuvable: " + id));
        String target = status == null ? "" : status.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(target)) {
            throw new IllegalArgumentException("Statut inconnu: " + status);
        }
        if ("CLOSED".equals(current.status())) {
            throw new IllegalStateException("Un incident clôturé ne se rouvre pas.");
        }
        if ("CLOSED".equals(target) && "OPEN".equals(current.status())) {
            throw new IllegalStateException("Un incident doit être investigué avant d'être clôturé.");
        }
        SafetyIncident updated = new SafetyIncident(current.id(), current.incidentNumber(), current.materialId(),
                current.materialName(), current.incidentDate(), current.incidentType(), current.severity(),
                current.description(), current.actionsTaken(), target,
                "CLOSED".equals(target) ? LocalDateTime.now() : null, current.reportedByName());
        incidents.replaceAll(row -> row.id() == id ? updated : row);

        return updated;
    }

    // ------------------------------------------------------------------ helpers

    private List<SafetyIncident> filteredIncidents(String status, String severity, String search) {
        String state = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String gravity = severity == null ? "" : severity.trim().toUpperCase(Locale.ROOT);
        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        return incidents.stream()
                .filter(row -> state.isEmpty() || row.status().equals(state))
                .filter(row -> gravity.isEmpty() || row.severity().equals(gravity))
                .filter(row -> needle.isEmpty()
                        || row.incidentNumber().toLowerCase(Locale.ROOT).contains(needle)
                        || row.description().toLowerCase(Locale.ROOT).contains(needle)
                        || row.materialName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(SafetyIncident::incidentDate).reversed())
                .toList();
    }

    /** Recomputes the derived fields of the register: the sheet state of every material. */
    private void refresh() {
        List<MaterialHazard> updated = new ArrayList<>();
        for (MaterialHazard material : materials) {
            List<SafetySheet> own = sheets.stream().filter(sheet -> sheet.materialId() == material.id()
                    && "SDS".equals(sheet.documentType())).toList();
            SafetySheet current = own.stream().filter(SafetySheet::isCurrent)
                    .max(Comparator.comparing(SafetySheet::issueDate,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            int instructionCount = (int) instructions.stream()
                    .filter(row -> row.materialId() == material.id()).count();
            String[] codes = materialPictograms.stream().filter(row -> row[0].equals(material.code()))
                    .findFirst().orElse(null);
            String hazardPictograms = codes == null ? ""
                    : String.join(",", java.util.Arrays.copyOfRange(codes, 1, codes.length));
            int pictogramCount = codes == null ? 0 : codes.length - 1;
            int incidentCount = (int) incidents.stream()
                    .filter(row -> row.materialId() != null && row.materialId() == material.id()).count();
            updated.add(new MaterialHazard(material.id(), material.code(), material.commercialName(),
                    material.chemicalName(), material.hazardClass(), hazardPictograms,
                    material.storageConditions(), material.storageLocation(), material.isHazardous(),
                    current != null, current == null ? null : current.id(),
                    current == null ? null : current.issueDate(), current == null ? null : current.revision(),
                    current != null && current.isObsolete(), instructionCount, pictogramCount, incidentCount));
        }
        materials.clear();
        materials.addAll(updated);
    }

    private MaterialFile file(MaterialHazard material) {
        List<SafetySheet> own = sheets.stream().filter(sheet -> sheet.materialId() == material.id())
                .sorted(Comparator.comparing(SafetySheet::issueDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        SafetyInstructions rules = instructions.stream().filter(row -> row.materialId() == material.id())
                .findFirst().orElse(null);
        List<String> codes = materialPictograms.stream().filter(row -> row[0].equals(material.code()))
                .findFirst().map(row -> List.of(java.util.Arrays.copyOfRange(row, 1, row.length)))
                .orElse(List.of());
        List<HazardPictogram> own_pictograms = pictograms.stream().filter(p -> codes.contains(p.code())).toList();
        List<SafetyIncident> history = incidents.stream()
                .filter(row -> row.materialId() != null && row.materialId() == material.id())
                .sorted(Comparator.comparing(SafetyIncident::incidentDate).reversed())
                .limit(10).toList();

        return new MaterialFile(material, own, rules, own_pictograms, history);
    }

    private int openIncidents() {
        return (int) incidents.stream().filter(SafetyIncident::isOpen).count();
    }

    private static boolean obsolete(LocalDate issueDate) {
        return issueDate == null || issueDate.isBefore(LocalDate.now().minusYears(SHEET_VALIDITY_YEARS));
    }

    private static <T> PagedResult<T> page(List<T> rows, int page, int pageSize) {
        int size = Math.max(1, pageSize);
        int index = Math.max(1, page);
        int from = Math.min(rows.size(), (index - 1) * size);

        return new PagedResult<>(rows.subList(from, Math.min(rows.size(), from + size)), index - 1, size,
                rows.size());
    }
}
