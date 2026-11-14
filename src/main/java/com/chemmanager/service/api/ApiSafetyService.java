package com.chemmanager.service.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
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
import com.fasterxml.jackson.databind.JsonNode;

/** The chemical register as the server keeps it, served by {@code /api/safety}. */
public final class ApiSafetyService implements SafetyService {

    private final ApiClient client;

    public ApiSafetyService(ApiClient client) {
        this.client = client;
    }

    @Override
    public SafetyOverview overview() {
        JsonNode node = client.get(ApiEndpoints.SAFETY_OVERVIEW, JsonNode.class);
        return new SafetyOverview(intOf(node, "materials"), intOf(node, "hazardousMaterials"),
                intOf(node, "materialsWithSheet"), intOf(node, "materialsWithoutSheet"),
                intOf(node, "currentSheets"), intOf(node, "obsoleteSheets"),
                intOf(node, "materialsWithInstructions"), intOf(node, "incidents"),
                intOf(node, "openIncidents"), intOf(node, "severeIncidents"),
                intOf(node, "sheetValidityYears"));
    }

    @Override
    public List<HazardPictogram> pictograms() {
        JsonNode node = client.get(ApiEndpoints.SAFETY_PICTOGRAMS, JsonNode.class);
        List<HazardPictogram> pictograms = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode row : node) {
                pictograms.add(new HazardPictogram(row.path("id").asLong(), text(row, "code"),
                        text(row, "nameFr"), text(row, "nameAr"), text(row, "nameEn"),
                        text(row, "description"), intOf(row, "materialCount")));
            }
        }

        return pictograms;
    }

    @Override
    public PagedResult<MaterialHazard> materials(int page, int pageSize, String search, String risk) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", search == null ? "" : search);
        params.put("risk", risk == null ? "" : risk);
        params.put("page", Integer.toString(Math.max(1, page)));
        params.put("pageSize", Integer.toString(pageSize));

        return client.getPage(ApiEndpoints.SAFETY_MATERIALS, params, JsonNode.class)
                .map(ApiSafetyService::materialRow);
    }

    @Override
    public MaterialFile material(long id) {
        return file(client.get(ApiEndpoints.SAFETY_MATERIAL + "/" + id, JsonNode.class));
    }

    @Override
    public PagedResult<SafetySheet> sheets(int page, int pageSize, String search, String documentType,
                                           boolean currentOnly) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", search == null ? "" : search);
        params.put("documentType", documentType == null ? "" : documentType);
        params.put("currentOnly", currentOnly ? "1" : "0");
        params.put("page", Integer.toString(Math.max(1, page)));
        params.put("pageSize", Integer.toString(pageSize));

        return client.getPage(ApiEndpoints.SAFETY_SHEETS, params, JsonNode.class)
                .map(ApiSafetyService::sheet);
    }

    @Override
    public MaterialFile registerSheet(NewSheet sheet) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("materialId", sheet.materialId());
        body.put("documentType", sheet.documentType() == null ? "SDS" : sheet.documentType());
        body.put("filePath", sheet.filePath());
        body.put("revision", sheet.revision() == null ? "" : sheet.revision());
        body.put("source", sheet.source() == null ? "" : sheet.source());
        if (sheet.issueDate() != null) {
            body.put("issueDate", sheet.issueDate().toString());
        }

        return file(client.post(ApiEndpoints.SAFETY_SHEETS, body, JsonNode.class));
    }

    @Override
    public MaterialFile saveInstructions(long materialId, InstructionUpdate update) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ppe", update.ppe() == null ? "" : update.ppe());
        body.put("storage", update.storage() == null ? "" : update.storage());
        body.put("handling", update.handling() == null ? "" : update.handling());
        body.put("firstAid", update.firstAid() == null ? "" : update.firstAid());
        body.put("fireMeasures", update.fireMeasures() == null ? "" : update.fireMeasures());
        body.put("spillage", update.spillage() == null ? "" : update.spillage());

        return file(client.post(ApiEndpoints.SAFETY_MATERIAL + "/" + materialId + "/instructions", body,
                JsonNode.class));
    }

    @Override
    public MaterialFile setPictograms(long materialId, List<String> codes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pictograms", codes == null ? List.of() : codes);

        return file(client.post(ApiEndpoints.SAFETY_MATERIAL + "/" + materialId + "/pictograms", body,
                JsonNode.class));
    }

    @Override
    public PagedResult<SafetyIncident> incidents(int page, int pageSize, String status, String severity,
                                                 String search) {
        return client.getPage(ApiEndpoints.SAFETY_INCIDENTS, incidentQuery(page, pageSize, status, severity, search),
                JsonNode.class).map(ApiSafetyService::incidentRow);
    }

    @Override
    public IncidentSummary incidentSummary(String status, String severity, String search) {
        JsonNode root = client.getTree(ApiEndpoints.SAFETY_INCIDENTS,
                incidentQuery(1, 1, status, severity, search));
        JsonNode summary = root == null ? null : root.path("meta").path("summary");
        return new IncidentSummary(intOf(summary, "total"), intOf(summary, "open"), intOf(summary, "severe"));
    }

    @Override
    public SafetyIncident reportIncident(NewIncident incident) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (incident.materialId() != null && incident.materialId() > 0) {
            body.put("materialId", incident.materialId());
        }
        body.put("incidentType", incident.incidentType());
        body.put("severity", incident.severity());
        body.put("description", incident.description());
        body.put("actionsTaken", incident.actionsTaken() == null ? "" : incident.actionsTaken());

        return incidentRow(client.post(ApiEndpoints.SAFETY_INCIDENTS, body, JsonNode.class));
    }

    @Override
    public SafetyIncident changeIncidentStatus(long id, String status) {
        return incidentRow(client.post(ApiEndpoints.SAFETY_INCIDENTS + "/" + id + "/status",
                Map.of("status", status), JsonNode.class));
    }

    private static Map<String, String> incidentQuery(int page, int pageSize, String status, String severity,
                                                     String search) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("status", status == null ? "" : status);
        params.put("severity", severity == null ? "" : severity);
        params.put("search", search == null ? "" : search);
        params.put("page", Integer.toString(Math.max(1, page)));
        params.put("pageSize", Integer.toString(pageSize));

        return params;
    }

    private static MaterialFile file(JsonNode node) {
        if (node == null) {
            return null;
        }
        List<SafetySheet> sheets = new ArrayList<>();
        for (JsonNode row : node.path("sheets")) {
            sheets.add(sheet(row));
        }
        List<HazardPictogram> pictograms = new ArrayList<>();
        for (JsonNode row : node.path("pictograms")) {
            pictograms.add(new HazardPictogram(0, text(row, "code"), text(row, "nameFr"),
                    text(row, "nameAr"), text(row, "nameEn"), "", 0));
        }
        List<SafetyIncident> incidents = new ArrayList<>();
        for (JsonNode row : node.path("incidents")) {
            incidents.add(new SafetyIncident(row.path("id").asLong(), text(row, "incidentNumber"),
                    null, "", dateTime(row, "incidentDate"), text(row, "incidentType"), text(row, "severity"),
                    "", "", text(row, "status"), null, ""));
        }
        JsonNode instructions = node.path("instructions");
        SafetyInstructions rules = instructions == null || instructions.isMissingNode() || instructions.isNull()
                ? null
                : new SafetyInstructions(instructions.path("id").asLong(-1), node.path("id").asLong(),
                        text(instructions, "ppe"), text(instructions, "storage"), text(instructions, "handling"),
                        text(instructions, "firstAid"), text(instructions, "fireMeasures"),
                        text(instructions, "spillage"));

        return new MaterialFile(materialRow(node), sheets, rules, pictograms, incidents);
    }

    private static MaterialHazard materialRow(JsonNode row) {
        JsonNode issueDate = row == null ? null : row.path("sheetIssueDate");
        LocalDate sheetDate = issueDate == null || issueDate.isNull() ? null
                : ApiSalesService.date(row, "sheetIssueDate");
        Long sheetId = row == null || row.path("sheetId").isNull() || row.path("sheetId").isMissingNode()
                ? null : row.path("sheetId").asLong();

        return new MaterialHazard(row == null ? 0 : row.path("id").asLong(), text(row, "code"),
                text(row, "commercialName"), text(row, "chemicalName"), text(row, "hazardClass"),
                text(row, "hazardPictograms"), text(row, "storageConditions"), text(row, "storageLocation"),
                boolOf(row, "isHazardous"), boolOf(row, "hasSheet"), sheetId, sheetDate,
                text(row, "sheetRevision"), boolOf(row, "sheetObsolete"), intOf(row, "instructionCount"),
                intOf(row, "pictogramCount"), intOf(row, "incidentCount"));
    }

    private static SafetySheet sheet(JsonNode row) {
        JsonNode current = row == null ? null : row.path("isCurrent");
        return new SafetySheet(row == null ? 0 : row.path("id").asLong(), row == null ? 0
                : row.path("materialId").asLong(), text(row, "materialCode"), text(row, "commercialName"),
                text(row, "documentType"), text(row, "filePath"), ApiSalesService.date(row, "issueDate"),
                text(row, "revision"), text(row, "source"),
                current != null && (current.asBoolean() || "1".equals(current.asText())),
                boolOf(row, "isObsolete"), dateTime(row, "createdAt"));
    }

    private static SafetyIncident incidentRow(JsonNode row) {
        JsonNode materialId = row == null ? null : row.path("materialId");
        return new SafetyIncident(row == null ? 0 : row.path("id").asLong(), text(row, "incidentNumber"),
                materialId == null || materialId.isNull() ? null : materialId.asLong(),
                text(row, "materialName"), dateTime(row, "incidentDate"), text(row, "incidentType"),
                text(row, "severity"), text(row, "description"), text(row, "actionsTaken"),
                text(row, "status"), dateTime(row, "closedAt"), text(row, "reportedByName"));
    }

    private static LocalDateTime dateTime(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        String raw = value.asText().replace(' ', 'T');
        try {
            return LocalDateTime.parse(raw.length() > 19 ? raw.substring(0, 19) : raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static int intOf(JsonNode node, String field) {
        return node == null ? 0 : node.path(field).asInt(0);
    }

    private static boolean boolOf(JsonNode node, String field) {
        if (node == null) {
            return false;
        }
        JsonNode value = node.path(field);
        return value.asBoolean(false) || "1".equals(value.asText());
    }
}
