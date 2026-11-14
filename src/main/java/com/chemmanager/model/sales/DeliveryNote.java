package com.chemmanager.model.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A delivery note: the exact batches that left the warehouse for a customer, and the signature that
 * proves they arrived.
 *
 * <p>Stock leaves when the note is prepared (the server picks the batches FEFO), so a note is the
 * physical proof of shipment, not an intention.</p>
 */
public record DeliveryNote(
        long id,
        String noteNumber,
        long orderId,
        String orderNumber,
        long customerId,
        String customerName,
        String status,
        LocalDate preparedAt,
        LocalDate deliveredAt,
        String signedBy,
        String routeNumber,
        String notes,
        BigDecimal totalQuantity,
        int lineCount,
        List<Line> items) {

    public DeliveryNote {
        items = items == null ? List.of() : List.copyOf(items);
        totalQuantity = totalQuantity == null ? BigDecimal.ZERO : totalQuantity;
    }

    public boolean signable() {
        return "PREPARED".equals(status) || "LOADED".equals(status) || "IN_TRANSIT".equals(status);
    }

    public boolean loadable() {
        return "PREPARED".equals(status);
    }

    /** A delivered batch: the traceability chain the customer receives with the goods. */
    public record Line(long id, long productId, String productCode, String productName, String unit,
                       long productBatchId, String batchNumber, BigDecimal quantity) {
    }
}
