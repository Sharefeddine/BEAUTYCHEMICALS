package com.chemmanager.dto;

/** Filters of the stock list: warehouse, kind of item, quality status and expiry horizon. */
public record StockQuery(String search, Long warehouseId, String kind, String quality, int expiringDays,
                         boolean availableOnly, int page, int pageSize) {

    public static StockQuery initial(int pageSize) {
        return new StockQuery("", null, "", "", 0, true, 0, pageSize);
    }

    public StockQuery withSearch(String value) {
        return new StockQuery(value, warehouseId, kind, quality, expiringDays, availableOnly, 0, pageSize);
    }

    public StockQuery withWarehouse(Long value) {
        return new StockQuery(search, value, kind, quality, expiringDays, availableOnly, 0, pageSize);
    }

    public StockQuery withKind(String value) {
        return new StockQuery(search, warehouseId, value, quality, expiringDays, availableOnly, 0, pageSize);
    }

    public StockQuery withQuality(String value) {
        return new StockQuery(search, warehouseId, kind, value, expiringDays, availableOnly, 0, pageSize);
    }

    public StockQuery withExpiringDays(int value) {
        return new StockQuery(search, warehouseId, kind, quality, value, availableOnly, 0, pageSize);
    }

    public StockQuery withAvailableOnly(boolean value) {
        return new StockQuery(search, warehouseId, kind, quality, expiringDays, value, 0, pageSize);
    }

    public StockQuery withPage(int value) {
        return new StockQuery(search, warehouseId, kind, quality, expiringDays, availableOnly, value, pageSize);
    }

    public StockQuery withPageSize(int value) {
        return new StockQuery(search, warehouseId, kind, quality, expiringDays, availableOnly, page, value);
    }
}
