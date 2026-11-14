package com.chemmanager.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the warehouse is worth today, what expires, and what is below its minimum. */
public record StockReport(Totals totals, List<Warehouse> warehouses, List<Expiring> expiring,
                          List<LowStock> lowStock) {

    public record Totals(BigDecimal totalValue, int expiringCount, int expiredCount, int quarantineCount,
                         int lowStockCount) {
    }

    public record Warehouse(long id, String code, String name, BigDecimal productValue,
                            BigDecimal materialValue, BigDecimal totalValue) {
    }

    public record Expiring(String productName, String batchNumber, String warehouseName, BigDecimal quantity,
                           LocalDate expiryDate, int daysLeft, String qualityStatus) {
    }

    public record LowStock(String sku, String name, BigDecimal minStock, BigDecimal quantity) {
    }
}
