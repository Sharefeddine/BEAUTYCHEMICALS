package com.chemmanager.model.inventory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One batch in one warehouse: the unit the warehouse actually works with. Stock is never a single
 * number on a product, it is the list of these rows.
 */
public record StockBatch(String itemKind, long batchId, long itemId, String itemCode, String itemName,
                         String batchNumber, long warehouseId, String warehouseName, BigDecimal quantity,
                         String unit, LocalDate productionDate, LocalDate expiryDate, Integer daysToExpiry,
                         boolean expired, String qualityStatus, BigDecimal unitCost, BigDecimal stockValue,
                         String supplierName) {

    public boolean isMaterial() {
        return "MATERIAL".equalsIgnoreCase(itemKind);
    }

    public boolean isQuarantined() {
        return "QUARANTINE".equalsIgnoreCase(qualityStatus);
    }

    public boolean isReleased() {
        return "RELEASED".equalsIgnoreCase(qualityStatus);
    }
}
