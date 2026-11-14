package com.chemmanager.model.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What the "receive" dialog produces: the warehouse, whether the goods are released straight away,
 * and one entry per arrived line with its batch number and expiry.
 */
public record ReceptionDraft(long warehouseId, boolean release, String notes, List<Line> lines) {

    public ReceptionDraft {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record Line(long orderItemId, BigDecimal quantity, String batchNumber, LocalDate expiryDate,
                       LocalDate productionDate) {
    }
}
