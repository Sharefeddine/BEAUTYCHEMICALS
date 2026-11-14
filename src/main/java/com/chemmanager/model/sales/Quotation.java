package com.chemmanager.model.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer quotation and the lines it prices.
 *
 * <p>A quotation is a priced promise: accepting it creates the order, so the client never edits a
 * quote that has already been sent.</p>
 */
public record Quotation(
        long id,
        String quoteNumber,
        long customerId,
        String customerName,
        String status,
        LocalDate quoteDate,
        LocalDate validUntil,
        BigDecimal amountHt,
        BigDecimal vatAmount,
        BigDecimal totalTtc,
        String notes,
        List<Line> items) {

    public Quotation {
        items = items == null ? List.of() : List.copyOf(items);
        amountHt = amountHt == null ? BigDecimal.ZERO : amountHt;
        vatAmount = vatAmount == null ? BigDecimal.ZERO : vatAmount;
        totalTtc = totalTtc == null ? BigDecimal.ZERO : totalTtc;
    }

    /** True when the quote can still be sent, accepted or rejected. */
    public boolean open() {
        return "DRAFT".equals(status) || "SENT".equals(status);
    }

    public record Line(long id, long productId, String productCode, String productName, String unit,
                       BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct, BigDecimal vatRate,
                       BigDecimal lineTotal) {
    }

    /** A line the user typed in the quotation dialog. */
    public record NewLine(long productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct) {
    }

    /** The whole quotation request: who, until when, and what. */
    public record NewQuotation(long customerId, List<NewLine> items, LocalDate validUntil, String notes) {

        public NewQuotation {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
