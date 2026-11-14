package com.chemmanager.model.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of the append-only stock ledger: who moved what, from where to where, and why.
 */
public record StockMovement(long id, String movementNumber, String movementType, String itemKind,
                            long batchId, String batchNumber, String itemCode, String itemName,
                            long warehouseId, String warehouseName, Long targetWarehouseId,
                            String targetWarehouseName, BigDecimal quantity, BigDecimal unitCost,
                            BigDecimal value, String referenceType, String reason, String userName,
                            LocalDateTime occurredAt) {

    public boolean isTransfer() {
        return "TRANSFER".equalsIgnoreCase(movementType);
    }

    public boolean isOutgoing() {
        return "OUT".equalsIgnoreCase(movementType) || "PRODUCTION_OUT".equalsIgnoreCase(movementType)
                || "WASTE".equalsIgnoreCase(movementType);
    }
}
