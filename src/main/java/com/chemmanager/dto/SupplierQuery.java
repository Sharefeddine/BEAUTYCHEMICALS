package com.chemmanager.dto;

/** Filters of the supplier list. */
public record SupplierQuery(String search, boolean activeOnly, int page, int pageSize) {

    public static SupplierQuery initial(int pageSize) {
        return new SupplierQuery("", false, 0, pageSize);
    }

    public SupplierQuery withSearch(String value) {
        return new SupplierQuery(value, activeOnly, 0, pageSize);
    }

    public SupplierQuery withActiveOnly(boolean value) {
        return new SupplierQuery(search, value, 0, pageSize);
    }

    public SupplierQuery withPage(int value) {
        return new SupplierQuery(search, activeOnly, value, pageSize);
    }

    public SupplierQuery withPageSize(int value) {
        return new SupplierQuery(search, activeOnly, page, value);
    }
}
