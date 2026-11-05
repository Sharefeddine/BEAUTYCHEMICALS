package com.chemmanager.service.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.MaterialQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.service.RawMaterialService;
import com.fasterxml.jackson.core.type.TypeReference;

/** Raw material catalogue served by {@code GET /raw-materials}. */
public final class ApiRawMaterialService implements RawMaterialService {

    private final ApiClient client;

    public ApiRawMaterialService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<RawMaterial> search(MaterialQuery query) {
        MaterialQuery effective = query == null ? MaterialQuery.initial(20) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", effective.search());
        params.put("category", effective.category());
        params.put("hazardous_only", Boolean.toString(effective.hazardousOnly()));
        params.put("low_stock_only", Boolean.toString(effective.lowStockOnly()));
        params.put("page", Integer.toString(effective.page()));
        params.put("page_size", Integer.toString(effective.pageSize()));
        return client.get(ApiEndpoints.RAW_MATERIALS, params, new TypeReference<PagedResult<RawMaterial>>() {
        });
    }

    @Override
    public List<String> categories() {
        return client.get(ApiEndpoints.MATERIAL_CATEGORIES, Map.of(), new TypeReference<List<String>>() {
        });
    }
}
