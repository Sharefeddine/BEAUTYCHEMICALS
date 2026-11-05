package com.chemmanager.model;

/** Commercial nature of an article, drives costing and inventory rules. */
public enum ProductType {
    MANUFACTURED("product.type.manufactured"),
    TRADED("product.type.traded"),
    RAW_MATERIAL("product.type.raw_material"),
    PACKAGING("product.type.packaging");

    private final String labelKey;

    ProductType(String labelKey) {
        this.labelKey = labelKey;
    }

    public String labelKey() {
        return labelKey;
    }
}
