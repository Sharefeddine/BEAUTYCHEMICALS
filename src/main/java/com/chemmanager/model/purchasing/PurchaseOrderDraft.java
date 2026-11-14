package com.chemmanager.model.purchasing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the "new order" dialog produces before the server numbers and totals it. */
public record PurchaseOrderDraft(long supplierId, LocalDate expectedDate, String notes, List<Line> lines) {

    public PurchaseOrderDraft {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record Line(long materialId, BigDecimal quantity, String unit, BigDecimal unitPrice,
                       BigDecimal vatRate) {
    }
}
