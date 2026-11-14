package com.chemmanager.model.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A supplier order with its lines and its receptions.
 *
 * <p>{@code DRAFT} orders can still be changed or cancelled, a {@code SENT} order is with the
 * supplier, and once something arrives the order becomes {@code PARTIAL} then {@code RECEIVED};
 * from that point the stock is real and the order can no longer be cancelled.</p>
 */
public record PurchaseOrder(long id, String orderNumber, long supplierId, String supplierName, String status,
                            LocalDate orderDate, LocalDate expectedDate, BigDecimal amountHt,
                            BigDecimal vatAmount, BigDecimal totalTtc, String currency, String notes,
                            long itemCount, BigDecimal receivedQuantity, long receiptCount,
                            List<Line> items, List<Receipt> receipts) {

    public PurchaseOrder {
        items = items == null ? List.of() : List.copyOf(items);
        receipts = receipts == null ? List.of() : List.copyOf(receipts);
    }

    public boolean isDraft() {
        return "DRAFT".equalsIgnoreCase(status);
    }

    public boolean canBeReceived() {
        return "SENT".equalsIgnoreCase(status) || "PARTIAL".equalsIgnoreCase(status);
    }

    public boolean canBeCancelled() {
        return !"CANCELLED".equalsIgnoreCase(status) && !"RECEIVED".equalsIgnoreCase(status)
                && (receivedQuantity == null || receivedQuantity.signum() == 0);
    }

    /** One ordered line; {@code remainingQty} is what the supplier still owes. */
    public record Line(long id, long materialId, String materialCode, String materialName, BigDecimal quantity,
                       String unit, BigDecimal unitPrice, BigDecimal vatRate, BigDecimal receivedQty,
                       BigDecimal remainingQty, BigDecimal lineTotal) {
    }

    /** One goods reception against this order. */
    public record Receipt(long id, String receiptNumber, long warehouseId, String warehouseName,
                          LocalDateTime receivedAt, String receivedByName, long itemCount) {
    }
}
