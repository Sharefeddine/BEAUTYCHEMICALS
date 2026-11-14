package com.chemmanager.model.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The document produced when goods arrive: which batches entered which warehouse. */
public record GoodsReceipt(long id, String receiptNumber, long orderId, String orderNumber, long warehouseId,
                           String warehouseName, LocalDateTime receivedAt, String receivedByName,
                           String notes, String orderStatus, List<Line> items) {

    public GoodsReceipt {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Line(long id, long materialId, String materialCode, String materialName, BigDecimal quantity,
                       BigDecimal unitPrice, String batchNumber, LocalDate expiryDate, BigDecimal batchQuantity,
                       String qualityStatus) {
    }
}
