package com.chemmanager.service.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.PurchaseQuery;
import com.chemmanager.model.purchasing.GoodsReceipt;
import com.chemmanager.model.purchasing.PurchaseOrder;
import com.chemmanager.model.purchasing.PurchaseOrderDraft;
import com.chemmanager.model.purchasing.ReceptionDraft;
import com.chemmanager.service.PurchasingService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Purchase orders and receptions served by {@code /api/purchaseOrders}.
 *
 * <p>The totals are computed server-side from the lines, the order number is generated there, and
 * a reception creates the batch and its ledger entry in one transaction — the client never has to
 * keep the stock consistent.</p>
 */
public final class ApiPurchasingService implements PurchasingService {

    private final ApiClient client;

    public ApiPurchasingService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<PurchaseOrder> search(PurchaseQuery query) {
        PurchaseQuery effective = query == null ? PurchaseQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("status", effective.status());
        if (effective.supplierId() != null) {
            params.put("supplierId", Long.toString(effective.supplierId()));
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.PURCHASE_ORDERS, params, JsonNode.class)
                .map(ApiMapper::purchaseOrder);
    }

    @Override
    public PurchaseOrder load(long id) {
        return ApiMapper.purchaseOrder(client.get(ApiEndpoints.PURCHASE_ORDERS + "/" + id, JsonNode.class));
    }

    @Override
    public PurchaseOrder create(PurchaseOrderDraft draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("supplierId", draft.supplierId());
        body.put("expectedDate", draft.expectedDate() == null ? null : draft.expectedDate().toString());
        body.put("notes", draft.notes() == null ? "" : draft.notes());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (PurchaseOrderDraft.Line line : draft.lines()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialId", line.materialId());
            row.put("quantity", line.quantity());
            row.put("unit", line.unit());
            row.put("unitPrice", line.unitPrice());
            row.put("vatRate", line.vatRate());
            lines.add(row);
        }
        body.put("items", lines);

        return ApiMapper.purchaseOrder(client.post(ApiEndpoints.PURCHASE_ORDERS, body, JsonNode.class));
    }

    @Override
    public PurchaseOrder send(long id) {
        return ApiMapper.purchaseOrder(client.post(ApiEndpoints.PURCHASE_ORDERS + "/" + id + "/send", null,
                JsonNode.class));
    }

    @Override
    public PurchaseOrder cancel(long id) {
        return ApiMapper.purchaseOrder(client.post(ApiEndpoints.PURCHASE_ORDERS + "/" + id + "/cancel", null,
                JsonNode.class));
    }

    @Override
    public GoodsReceipt receive(long orderId, ReceptionDraft draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("warehouseId", draft.warehouseId());
        body.put("release", draft.release());
        body.put("notes", draft.notes() == null ? "" : draft.notes());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ReceptionDraft.Line line : draft.lines()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderItemId", line.orderItemId());
            row.put("quantity", line.quantity());
            row.put("batchNumber", line.batchNumber());
            row.put("expiryDate", line.expiryDate() == null ? null : line.expiryDate().toString());
            row.put("productionDate", line.productionDate() == null ? null : line.productionDate().toString());
            lines.add(row);
        }
        body.put("items", lines);

        return ApiMapper.goodsReceipt(client.post(ApiEndpoints.PURCHASE_ORDERS + "/" + orderId + "/receipts",
                body, JsonNode.class));
    }
}
