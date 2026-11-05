package com.chemmanager.dto;

import com.chemmanager.model.ProductType;

/** Filter + paging state of the product list. */
public record ProductQuery(String search, String category, ProductType type, boolean activeOnly,
                           int page, int pageSize) {

    public static ProductQuery initial(int pageSize) {
        return new ProductQuery(null, null, null, false, 0, pageSize);
    }

    public ProductQuery withSearch(String value) {
        return new ProductQuery(value, category, type, activeOnly, 0, pageSize);
    }

    public ProductQuery withCategory(String value) {
        return new ProductQuery(search, value, type, activeOnly, 0, pageSize);
    }

    public ProductQuery withType(ProductType value) {
        return new ProductQuery(search, category, value, activeOnly, 0, pageSize);
    }

    public ProductQuery withActiveOnly(boolean value) {
        return new ProductQuery(search, category, type, value, 0, pageSize);
    }

    public ProductQuery withPage(int value) {
        return new ProductQuery(search, category, type, activeOnly, value, pageSize);
    }

    public ProductQuery withPageSize(int value) {
        return new ProductQuery(search, category, type, activeOnly, 0, value);
    }
}
