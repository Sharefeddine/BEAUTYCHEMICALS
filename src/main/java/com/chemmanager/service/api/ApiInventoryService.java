package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.MovementQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.StockQuery;
import com.chemmanager.model.inventory.InventorySummary;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.model.inventory.StockMovement;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.service.InventoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Inventory served by {@code /api/inventory}.
 *
 * <p>The server owns every rule: FEFO order, quarantine walls, the "no silent stock" rule that
 * refuses an adjustment without a difference, and the ledger row that records who did what.</p>
 */
public final class ApiInventoryService implements InventoryService {

    private final ApiClient client;

    public ApiInventoryService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<Warehouse> warehouses() {
        List<JsonNode> rows = client.get(ApiEndpoints.INVENTORY_WAREHOUSES, Map.of(),
                new TypeReference<List<JsonNode>>() {
                });
        List<Warehouse> warehouses = new ArrayList<>();
        if (rows != null) {
            for (JsonNode row : rows) {
                warehouses.add(ApiMapper.warehouse(row));
            }
        }
        return warehouses;
    }

    @Override
    public InventorySummary summary() {
        return ApiMapper.inventorySummary(client.get(ApiEndpoints.INVENTORY_SUMMARY, JsonNode.class));
    }

    @Override
    public PagedResult<StockBatch> stock(StockQuery query) {
        StockQuery effective = query == null ? StockQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("kind", effective.kind());
        params.put("qualityStatus", effective.quality());
        if (effective.warehouseId() != null) {
            params.put("warehouseId", Long.toString(effective.warehouseId()));
        }
        if (effective.expiringDays() > 0) {
            params.put("expiringDays", Integer.toString(effective.expiringDays()));
        }
        if (effective.availableOnly()) {
            params.put("availableOnly", "1");
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.INVENTORY_STOCK, params, JsonNode.class)
                .map(ApiMapper::stockBatch);
    }

    @Override
    public PagedResult<StockMovement> movements(MovementQuery query) {
        MovementQuery effective = query == null ? MovementQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("kind", effective.kind());
        params.put("movementType", effective.movementType());
        if (effective.batchId() != null) {
            params.put("batchId", Long.toString(effective.batchId()));
        }
        if (effective.warehouseId() != null) {
            params.put("warehouseId", Long.toString(effective.warehouseId()));
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.INVENTORY_MOVEMENTS, params, JsonNode.class)
                .map(ApiMapper::stockMovement);
    }

    @Override
    public void adjust(StockBatch batch, BigDecimal countedQuantity, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemKind", batch.itemKind());
        body.put("batchId", batch.batchId());
        body.put("countedQuantity", countedQuantity);
        body.put("reason", reason);
        client.postVoid(ApiEndpoints.INVENTORY_ADJUST, body);
    }

    @Override
    public void transfer(StockBatch batch, long targetWarehouseId, BigDecimal quantity, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("itemKind", batch.itemKind());
        body.put("batchId", batch.batchId());
        body.put("targetWarehouseId", targetWarehouseId);
        body.put("quantity", quantity);
        body.put("reason", reason);
        client.postVoid(ApiEndpoints.INVENTORY_TRANSFER, body);
    }
}
