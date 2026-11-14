package com.chemmanager.dto;

/** Filters of the manufacturing order list. */
public record ProductionQuery(String search, String status, int page, int pageSize) {

    public static ProductionQuery initial(int pageSize) {
        return new ProductionQuery("", "", 0, pageSize);
    }

    public ProductionQuery withSearch(String value) {
        return new ProductionQuery(value, status, 0, pageSize);
    }

    public ProductionQuery withStatus(String value) {
        return new ProductionQuery(search, value, 0, pageSize);
    }

    public ProductionQuery withPage(int value) {
        return new ProductionQuery(search, status, value, pageSize);
    }

    public ProductionQuery withPageSize(int value) {
        return new ProductionQuery(search, status, page, value);
    }
}
