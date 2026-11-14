package com.chemmanager.model.inventory;

import java.math.BigDecimal;

/**
 * Header figures of the inventory screen, computed by the server from the batch rows so the
 * client never keeps a second, drifting copy of the stock.
 */
public record InventorySummary(long batchCount, long materialBatchCount, long productBatchCount,
                               BigDecimal stockValue, long quarantineBatches, long expiringBatches,
                               int expiryWarningDays, long lowStockItems, long warehouseCount,
                               String currency) {
}
