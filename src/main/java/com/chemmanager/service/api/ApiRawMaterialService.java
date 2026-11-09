package com.chemmanager.service.api;

import java.util.ArrayList;
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
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Raw material catalogue served by {@code GET /api/rawMaterials}.
 *
 * <p>The hazardous-only and low-stock-only switches are server filters ({@code hazardous},
 * {@code lowStock}) so the pagination totals stay honest.</p>
 */
public final class ApiRawMaterialService implements RawMaterialService {

    private final ApiClient client;
    private List<String> categoryNames;
    private Map<String, Long> categoryIds;

    public ApiRawMaterialService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<RawMaterial> search(MaterialQuery query) {
        MaterialQuery effective = query == null ? MaterialQuery.initial(20) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("categoryId", categoryId(effective.category()));
        params.put("hazardous", effective.hazardousOnly() ? "1" : null);
        params.put("lowStock", effective.lowStockOnly() ? "1" : null);
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.RAW_MATERIALS, params, JsonNode.class).map(ApiMapper::material);
    }

    @Override
    public List<String> categories() {
        loadCategories();
        return categoryNames;
    }

    private String categoryId(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        loadCategories();
        Long id = categoryIds.get(categoryName);
        return id == null ? null : id.toString();
    }

    private void loadCategories() {
        if (categoryNames != null) {
            return;
        }
        List<JsonNode> rows = client.get(ApiEndpoints.MATERIAL_CATEGORIES, Map.of(),
                new TypeReference<List<JsonNode>>() {
                });
        List<String> names = new ArrayList<>();
        Map<String, Long> ids = new LinkedHashMap<>();
        if (rows != null) {
            for (JsonNode row : rows) {
                String name = row.path("nameFr").asText("");
                names.add(name);
                ids.put(name, row.path("id").asLong());
            }
        }
        categoryNames = List.copyOf(names);
        categoryIds = Map.copyOf(ids);
    }
}
