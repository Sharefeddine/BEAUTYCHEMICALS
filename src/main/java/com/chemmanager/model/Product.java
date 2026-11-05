package com.chemmanager.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A sellable finished product (or traded article).
 *
 * <p>Packaging variants of the same formula are stored as independent products that share a
 * formulation family, which is how the company sells 500 ml / 1 L / 5 L / 20 L / 200 L
 * versions of the same cleaner with their own SKU and prices.</p>
 */
public record Product(
        long id,
        String sku,
        String barcode,
        String nameFr,
        String nameAr,
        String description,
        String category,
        ProductType type,
        String unit,
        String packaging,
        BigDecimal packagingCapacity,
        int unitsPerCarton,
        int minStock,
        int maxStock,
        BigDecimal purchaseCost,
        BigDecimal productionCost,
        BigDecimal wholesalePrice,
        BigDecimal retailPrice,
        BigDecimal specialPrice,
        BigDecimal vatRate,
        int stockOnHand,
        String imagePath,
        boolean active) {

    public Product {
        Objects.requireNonNull(sku, "sku");
        nameFr = nameFr == null ? "" : nameFr.trim();
        nameAr = nameAr == null ? "" : nameAr.trim();
        unit = unit == null || unit.isBlank() ? "PCE" : unit;
        vatRate = vatRate == null ? BigDecimal.valueOf(19) : vatRate;
        purchaseCost = nullSafe(purchaseCost);
        productionCost = nullSafe(productionCost);
        wholesalePrice = nullSafe(wholesalePrice);
        retailPrice = nullSafe(retailPrice);
        specialPrice = nullSafe(specialPrice);
        packagingCapacity = nullSafe(packagingCapacity);
    }

    /** Localised display name with a graceful fallback to the other language. */
    public String displayName(boolean arabic) {
        if (arabic && !nameAr.isBlank()) {
            return nameAr;
        }
        if (!arabic && !nameFr.isBlank()) {
            return nameFr;
        }
        return nameFr.isBlank() ? nameAr : nameFr;
    }

    public BigDecimal unitCost(ProductType usage) {
        return type == ProductType.TRADED ? purchaseCost : productionCost;
    }

    public BigDecimal stockValue() {
        return unitCost(type).multiply(BigDecimal.valueOf(stockOnHand)).setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isLowStock() {
        return stockOnHand <= minStock;
    }

    public boolean isOutOfStock() {
        return stockOnHand <= 0;
    }

    public BigDecimal marginPercent() {
        if (retailPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal margin = retailPrice.subtract(unitCost(type));
        return margin.multiply(BigDecimal.valueOf(100)).divide(retailPrice, 1, RoundingMode.HALF_UP);
    }

    public Product withStock(int newStock) {
        return new Product(id, sku, barcode, nameFr, nameAr, description, category, type, unit, packaging,
                packagingCapacity, unitsPerCarton, minStock, maxStock, purchaseCost, productionCost,
                wholesalePrice, retailPrice, specialPrice, vatRate, newStock, imagePath, active);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
