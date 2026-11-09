package com.chemmanager.service.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductQuery;
import com.chemmanager.model.Product;
import com.chemmanager.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Product catalogue served by {@code GET /api/products}.
 *
 * <p>Filters map one-to-one onto the server query string ({@code search}, {@code categoryId},
 * {@code type}, {@code status}) and the pagination comes from the {@code meta} block, so a page
 * boundary is decided by the database and never by the client.</p>
 */
public final class ApiProductService implements ProductService {

    private final ApiClient client;
    private List<String> categoryNames;
    private Map<String, Long> categoryIds;

    public ApiProductService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<Product> search(ProductQuery query) {
        ProductQuery effective = query == null ? ProductQuery.initial(20) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("categoryId", categoryId(effective.category()));
        params.put("type", effective.type() == null ? null : effective.type().name());
        params.put("status", effective.activeOnly() ? "active" : null);
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.PRODUCTS, params, JsonNode.class).map(ApiMapper::product);
    }

    @Override
    public List<String> categories() {
        loadCategories();
        return categoryNames;
    }

    /**
     * The screens filter by the displayed category name, the API filters by {@code categoryId}:
     * the translation lives here so the UI never needs to know the identifiers.
     */
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
        List<JsonNode> rows = client.get(ApiEndpoints.PRODUCT_CATEGORIES, Map.of(),
                new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {
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

    @Override
    public Optional<Product> findById(long id) {
        try {
            JsonNode node = client.get(ApiEndpoints.PRODUCTS + "/" + id, JsonNode.class);
            return node == null ? Optional.empty() : Optional.of(ApiMapper.product(node));
        } catch (com.chemmanager.api.ApiException ex) {
            return Optional.empty();
        }
    }
}
