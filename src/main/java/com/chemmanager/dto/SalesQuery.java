package com.chemmanager.dto;

/** Search, status filter and pagination of the sales lists (quotations, orders, deliveries, invoices). */
public record SalesQuery(String search, String status, long customerId, int page, int pageSize) {

    public static SalesQuery initial(int pageSize) {
        return new SalesQuery("", "", 0, 0, pageSize);
    }

    public SalesQuery withSearch(String value) {
        return new SalesQuery(value == null ? "" : value, status, customerId, page, pageSize);
    }

    public SalesQuery withStatus(String value) {
        return new SalesQuery(search, value == null ? "" : value, customerId, page, pageSize);
    }

    public SalesQuery withPage(int value) {
        return new SalesQuery(search, status, customerId, value, pageSize);
    }

    public SalesQuery withPageSize(int value) {
        return new SalesQuery(search, status, customerId, page, value);
    }
}
