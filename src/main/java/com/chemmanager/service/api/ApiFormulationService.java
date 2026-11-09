package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.FormulationComparison;
import com.chemmanager.dto.FormulationQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.Formulation;
import com.chemmanager.model.FormulationItem;
import com.chemmanager.model.FormulationVersion;
import com.chemmanager.service.FormulationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Formulations served by {@code /api/formulations}.
 *
 * <p>The server owns the rules: an approved version is locked and answers {@code 409}, the cost is
 * recomputed from the current purchase prices on every read, and the approval is stamped with the
 * signed-in user. The client only renders the result.</p>
 */
public final class ApiFormulationService implements FormulationService {

    private final ApiClient client;

    public ApiFormulationService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<Formulation> search(FormulationQuery query) {
        FormulationQuery effective = query == null ? FormulationQuery.initial(20) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("status", effective.status());
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.FORMULATIONS, params, JsonNode.class).map(ApiMapper::formulation);
    }

    @Override
    public Formulation load(long id) {
        JsonNode node = client.get(path(id), JsonNode.class);
        return ApiMapper.formulation(node);
    }

    @Override
    public List<FormulationVersion> versions(long formulationId) {
        List<JsonNode> rows = client.get(path(formulationId) + "/versions", Map.of(),
                new TypeReference<List<JsonNode>>() {
                });
        List<FormulationVersion> versions = new ArrayList<>();
        if (rows != null) {
            for (JsonNode row : rows) {
                versions.add(ApiMapper.version(row));
            }
        }
        return versions;
    }

    @Override
    public FormulationVersion version(long formulationId, long versionId) {
        JsonNode node = client.get(path(formulationId) + "/versions/" + versionId, JsonNode.class);
        return ApiMapper.version(node);
    }

    @Override
    public Formulation create(String nameFr, String nameAr, Long productId, String notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nameFr", nameFr);
        body.put("nameAr", nameAr);
        body.put("productId", productId);
        body.put("notes", notes);
        return ApiMapper.formulation(client.post(ApiEndpoints.FORMULATIONS, body, JsonNode.class));
    }

    @Override
    public FormulationVersion createVersion(long formulationId) {
        JsonNode node = client.post(path(formulationId) + "/versions", null, JsonNode.class);
        return ApiMapper.version(node);
    }

    @Override
    public FormulationVersion updateVersion(long formulationId, long versionId, String instructions,
                                            BigDecimal expectedYield, String yieldUnit,
                                            List<FormulationItem> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instructions", instructions);
        body.put("expectedYield", expectedYield);
        body.put("yieldUnit", yieldUnit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FormulationItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", item.materialId());
            row.put("quantity", item.quantity());
            row.put("unit", item.unit());
            row.put("phase", item.phase());
            rows.add(row);
        }
        body.put("items", rows);

        return ApiMapper.version(client.put(path(formulationId) + "/versions/" + versionId, body, JsonNode.class));
    }

    @Override
    public FormulationVersion approve(long formulationId, long versionId) {
        JsonNode node = client.post(path(formulationId) + "/versions/" + versionId + "/approve", null,
                JsonNode.class);
        return ApiMapper.version(node);
    }

    @Override
    public Formulation archive(long formulationId) {
        return ApiMapper.formulation(client.post(path(formulationId) + "/archive", null, JsonNode.class));
    }

    @Override
    public FormulationComparison compare(long formulationId, long leftVersionId, long rightVersionId) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("left", Long.toString(leftVersionId));
        query.put("right", Long.toString(rightVersionId));
        JsonNode node = client.get(path(formulationId) + "/compare", query, JsonNode.class);

        List<FormulationComparison.Line> lines = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            lines.add(new FormulationComparison.Line(
                    row.path("materialCode").asText(""),
                    row.path("materialName").asText(""),
                    row.path("unit").asText(""),
                    ApiMapper.decimalOrZero(row, "leftQuantity"),
                    ApiMapper.decimalOrZero(row, "rightQuantity"),
                    ApiMapper.decimalOrZero(row, "delta"),
                    row.path("status").asText("UNCHANGED")));
        }

        return new FormulationComparison(
                node.path("left").path("version").asInt(0),
                node.path("right").path("version").asInt(0),
                ApiMapper.decimalOrZero(node.path("left"), "cost"),
                ApiMapper.decimalOrZero(node.path("right"), "cost"),
                ApiMapper.decimalOrZero(node, "costDelta"),
                lines);
    }

    private static String path(long formulationId) {
        return ApiEndpoints.FORMULATIONS + "/" + formulationId;
    }
}
