package com.chemmanager.service.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SupplierQuery;
import com.chemmanager.model.Supplier;
import com.chemmanager.service.SupplierService;
import com.fasterxml.jackson.databind.JsonNode;

/** Suppliers served by {@code /api/suppliers}. */
public final class ApiSupplierService implements SupplierService {

    private final ApiClient client;

    public ApiSupplierService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<Supplier> search(SupplierQuery query) {
        SupplierQuery effective = query == null ? SupplierQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("sort", "name");
        params.put("direction", "ASC");
        if (effective.activeOnly()) {
            params.put("activeOnly", "1");
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.SUPPLIERS, params, JsonNode.class).map(ApiMapper::supplier);
    }

    @Override
    public Supplier load(long id) {
        return ApiMapper.supplier(client.get(ApiEndpoints.SUPPLIERS + "/" + id, JsonNode.class));
    }
}
