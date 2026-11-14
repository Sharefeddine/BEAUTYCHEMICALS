package com.chemmanager.dto;

/** Filters of the purchase order list. */
public record PurchaseQuery(String search, String status, Long supplierId, int page, int pageSize) {

    public static PurchaseQuery initial(int pageSize) {
        return new PurchaseQuery("", "", null, 0, pageSize);
    }

    public PurchaseQuery withSearch(String value) {
        return new PurchaseQuery(value, status, supplierId, 0, pageSize);
    }

    public PurchaseQuery withStatus(String value) {
        return new PurchaseQuery(search, value, supplierId, 0, pageSize);
    }

    public PurchaseQuery withSupplier(Long value) {
        return new PurchaseQuery(search, status, value, 0, pageSize);
    }

    public PurchaseQuery withPage(int value) {
        return new PurchaseQuery(search, status, supplierId, value, pageSize);
    }

    public PurchaseQuery withPageSize(int value) {
        return new PurchaseQuery(search, status, supplierId, page, value);
    }
}
