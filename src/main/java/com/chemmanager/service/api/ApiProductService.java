package com.chemmanager.service.api;

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
import com.fasterxml.jackson.core.type.TypeReference;

/** Product catalogue served by {@code GET /products}. */
public final class ApiProductService implements ProductService {

    private final ApiClient client;

    public ApiProductService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<Product> search(ProductQuery query) {
        ProductQuery effective = query == null ? ProductQuery.initial(20) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("q", effective.search());
        params.put("category", effective.category());
        params.put("type", effective.type() == null ? null : effective.type().name());
        params.put("active_only", Boolean.toString(effective.activeOnly()));
        params.put("page", Integer.toString(effective.page()));
        params.put("page_size", Integer.toString(effective.pageSize()));
        return client.get(ApiEndpoints.PRODUCTS, params, new TypeReference<PagedResult<Product>>() {
        });
    }

    @Override
    public List<String> categories() {
        return client.get(ApiEndpoints.PRODUCT_CATEGORIES, Map.of(), new TypeReference<List<String>>() {
        });
    }

    @Override
    public Optional<Product> findById(long id) {
        try {
            return Optional.ofNullable(client.get(ApiEndpoints.PRODUCTS + "/" + id, Product.class));
        } catch (com.chemmanager.api.ApiException ex) {
            return Optional.empty();
        }
    }
}
