package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductionQuery;
import com.chemmanager.model.production.ApprovedVersion;
import com.chemmanager.model.production.ProductionGenealogy;
import com.chemmanager.model.production.ProductionOrder;
import com.chemmanager.service.ProductionService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Production served by {@code /api/production}.
 *
 * <p>Every rule lives on the server: FEFO reservation, consumption that cannot exceed a batch, the
 * quarantine wall around a finished batch until a PASS quality check, and the cost of the run.</p>
 */
public final class ApiProductionService implements ProductionService {

    private final ApiClient client;

    public ApiProductionService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<ApprovedVersion> versions() {
        JsonNode rows = client.get(ApiEndpoints.PRODUCTION_VERSIONS, JsonNode.class);
        List<ApprovedVersion> versions = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                versions.add(new ApprovedVersion(row.path("versionId").asLong(), row.path("version").asInt(),
                        text(row, "formulationCode"), text(row, "nameFr"), text(row, "nameAr"),
                        row.path("productId").asLong(), text(row, "productName"),
                        decimal(row, "expectedYield"), text(row, "yieldUnit"), row.path("itemCount").asInt()));
            }
        }
        return versions;
    }

    @Override
    public PagedResult<ProductionOrder> search(ProductionQuery query) {
        ProductionQuery effective = query == null ? ProductionQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("status", effective.status());
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.PRODUCTION_ORDERS, params, JsonNode.class)
                .map(ApiProductionService::order);
    }

    @Override
    public ProductionOrder load(long id) {
        return order(client.get(ApiEndpoints.PRODUCTION_ORDERS + "/" + id, JsonNode.class));
    }

    @Override
    public ProductionOrder create(ProductionOrder.NewOrder request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("formulationVersionId", request.formulationVersionId());
        body.put("warehouseId", request.warehouseId());
        body.put("plannedQuantity", request.plannedQuantity());
        if (request.plannedDate() != null) {
            body.put("plannedDate", request.plannedDate().toString());
        }
        body.put("notes", request.notes() == null ? "" : request.notes());

        return order(client.post(ApiEndpoints.PRODUCTION_ORDERS, body, JsonNode.class));
    }

    @Override
    public ProductionOrder start(long id) {
        return order(client.post(ApiEndpoints.PRODUCTION_ORDERS + "/" + id + "/start", Map.of(), JsonNode.class));
    }

    @Override
    public ProductionOrder consume(long id, List<ProductionOrder.Consumption> items) {
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ProductionOrder.Consumption item : items) {
            lines.add(Map.of("materialBatchId", item.materialBatchId(), "quantity", item.quantity()));
        }
        return order(client.post(ApiEndpoints.PRODUCTION_ORDERS + "/" + id + "/consume",
                Map.of("items", lines), JsonNode.class));
    }

    @Override
    public ProductionOrder complete(long id, ProductionOrder.Completion completion) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actualQuantity", completion.actualQuantity());
        body.put("wasteQuantity", completion.wasteQuantity() == null ? BigDecimal.ZERO : completion.wasteQuantity());
        body.put("packagingCost", completion.packagingCost() == null ? BigDecimal.ZERO : completion.packagingCost());
        body.put("otherCost", completion.otherCost() == null ? BigDecimal.ZERO : completion.otherCost());
        if (completion.expiryDate() != null) {
            body.put("expiryDate", completion.expiryDate().toString());
        }

        return order(client.post(ApiEndpoints.PRODUCTION_ORDERS + "/" + id + "/complete", body, JsonNode.class));
    }

    @Override
    public ProductionOrder quality(long id, ProductionOrder.QualityDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("checkType", decision.checkType());
        body.put("result", decision.result());
        body.put("remarks", decision.remarks() == null ? "" : decision.remarks());

        return order(client.post(ApiEndpoints.PRODUCTION_ORDERS + "/" + id + "/quality", body, JsonNode.class));
    }

    @Override
    public ProductionOrder cancel(long id, String reason) {
        return order(client.post(ApiEndpoints.PRODUCTION_ORDERS + "/" + id + "/cancel",
                Map.of("reason", reason == null ? "" : reason), JsonNode.class));
    }

    @Override
    public ProductionGenealogy genealogy(long id) {
        JsonNode node = client.get(ApiEndpoints.PRODUCTION_ORDERS + "/" + id + "/genealogy", JsonNode.class);
        JsonNode order = node.path("order");
        List<ProductionGenealogy.Input> inputs = new ArrayList<>();
        for (JsonNode row : node.path("inputs")) {
            inputs.add(new ProductionGenealogy.Input(text(row, "materialCode"), text(row, "materialName"),
                    text(row, "batchNumber"), decimal(row, "plannedQuantity"), decimal(row, "consumedQuantity"),
                    text(row, "unit"), text(row, "supplierName"), text(row, "receiptNumber"),
                    text(row, "purchaseOrderNumber"), text(row, "expiryDate")));
        }

        return new ProductionGenealogy(order.path("id").asLong(), text(order, "orderNumber"),
                text(order, "productName"), text(order, "formulationCode"), order.path("formulationVersion").asInt(),
                text(order, "status"), text(order, "plannedDate"), node.path("complete").asBoolean(),
                inputs, outputs(node.path("outputs")), checks(node.path("qualityChecks")));
    }

    static ProductionOrder order(JsonNode node) {
        List<ProductionOrder.PlanLine> plan = new ArrayList<>();
        for (JsonNode row : node.path("plan")) {
            plan.add(new ProductionOrder.PlanLine(row.path("materialId").asLong(), text(row, "materialCode"),
                    text(row, "materialName"), text(row, "unit"), decimal(row, "plannedQuantity"),
                    decimal(row, "unitPrice")));
        }
        List<ProductionOrder.MaterialLine> materials = new ArrayList<>();
        for (JsonNode row : node.path("materials")) {
            materials.add(new ProductionOrder.MaterialLine(row.path("id").asLong(),
                    row.path("materialBatchId").asLong(), text(row, "batchNumber"), text(row, "materialCode"),
                    text(row, "materialName"), decimal(row, "plannedQuantity"), decimal(row, "consumedQuantity"),
                    text(row, "unit"), decimal(row, "unitPrice"), decimal(row, "lineCost"),
                    decimal(row, "varianceQuantity"), date(row, "expiryDate"), text(row, "qualityStatus")));
        }

        return new ProductionOrder(node.path("id").asLong(), text(node, "orderNumber"),
                node.path("formulationVersion").asInt(), text(node, "formulationCode"),
                node.path("productId").asLong(), text(node, "productCode"), text(node, "productName"),
                node.path("warehouseId").asLong(), text(node, "warehouseName"), decimal(node, "plannedQuantity"),
                decimal(node, "actualQuantity"), decimal(node, "wasteQuantity"), text(node, "unit"),
                text(node, "status"), date(node, "plannedDate"), decimal(node, "materialCost"),
                decimal(node, "packagingCost"), decimal(node, "otherCost"), decimal(node, "totalCost"),
                decimal(node, "costPerUnit"), decimal(node, "standardMaterialCost"), decimal(node, "variance"),
                decimal(node, "variancePct"), decimal(node, "consumedQuantity"), node.path("sellable").asBoolean(),
                text(node, "notes"), plan, materials, outputs(node.path("outputs")), checks(node.path("qualityChecks")));
    }

    private static List<ProductionOrder.Output> outputs(JsonNode rows) {
        List<ProductionOrder.Output> outputs = new ArrayList<>();
        for (JsonNode row : rows) {
            outputs.add(new ProductionOrder.Output(row.path("productBatchId").asLong(), text(row, "batchNumber"),
                    decimal(row, "quantity"), text(row, "unit"), text(row, "qualityStatus"),
                    text(row, "warehouseName"), date(row, "productionDate"), date(row, "expiryDate"),
                    decimal(row, "unitCost"), row.path("sellable").asBoolean()));
        }
        return outputs;
    }

    private static List<ProductionOrder.QualityCheck> checks(JsonNode rows) {
        List<ProductionOrder.QualityCheck> checks = new ArrayList<>();
        for (JsonNode row : rows) {
            checks.add(new ProductionOrder.QualityCheck(row.path("id").asLong(), text(row, "checkType"),
                    text(row, "result"), text(row, "checkedAt"), text(row, "remarks"), text(row, "checkedByName")));
        }
        return checks;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return ApiMapper.decimalOrZero(node, field);
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    }
}
