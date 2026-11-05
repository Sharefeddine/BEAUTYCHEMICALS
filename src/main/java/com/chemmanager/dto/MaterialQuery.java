package com.chemmanager.dto;

/** Filter + paging state of the raw material list. */
public record MaterialQuery(String search, String category, boolean hazardousOnly, boolean lowStockOnly,
                            int page, int pageSize) {

    public static MaterialQuery initial(int pageSize) {
        return new MaterialQuery(null, null, false, false, 0, pageSize);
    }

    public MaterialQuery withSearch(String value) {
        return new MaterialQuery(value, category, hazardousOnly, lowStockOnly, 0, pageSize);
    }

    public MaterialQuery withCategory(String value) {
        return new MaterialQuery(search, value, hazardousOnly, lowStockOnly, 0, pageSize);
    }

    public MaterialQuery withHazardousOnly(boolean value) {
        return new MaterialQuery(search, category, value, lowStockOnly, 0, pageSize);
    }

    public MaterialQuery withLowStockOnly(boolean value) {
        return new MaterialQuery(search, category, hazardousOnly, value, 0, pageSize);
    }

    public MaterialQuery withPage(int value) {
        return new MaterialQuery(search, category, hazardousOnly, lowStockOnly, value, pageSize);
    }
}
