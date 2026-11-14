package com.chemmanager.model.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A sales invoice. Once issued it is frozen: the only corrections allowed are credit notes, which
 * the record exposes next to the payments.
 */
public record Invoice(
        long id,
        String invoiceNumber,
        long customerId,
        String customerName,
        long orderId,
        String orderNumber,
        String status,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amountHt,
        BigDecimal vatAmount,
        BigDecimal totalTtc,
        BigDecimal paidAmount,
        BigDecimal remaining,
        boolean finalized,
        boolean overdue,
        int printedCount,
        String notes,
        List<Line> items,
        List<Payment> payments,
        List<CreditNote> creditNotes) {

    public Invoice {
        items = items == null ? List.of() : List.copyOf(items);
        payments = payments == null ? List.of() : List.copyOf(payments);
        creditNotes = creditNotes == null ? List.of() : List.copyOf(creditNotes);
        totalTtc = totalTtc == null ? BigDecimal.ZERO : totalTtc;
        paidAmount = paidAmount == null ? BigDecimal.ZERO : paidAmount;
        remaining = remaining == null ? BigDecimal.ZERO : remaining;
    }

    public boolean draft() {
        return "DRAFT".equals(status);
    }

    public boolean payable() {
        return "ISSUED".equals(status) || "PARTIAL".equals(status);
    }

    public boolean creditable() {
        return "ISSUED".equals(status) || "PARTIAL".equals(status) || "PAID".equals(status);
    }

    public record Line(long id, long productId, String productCode, String productName, String unit,
                       String batchNumber, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct,
                       BigDecimal vatRate, BigDecimal lineTotal) {
    }

    /** A settlement booked against the invoice (or on the customer's account). */
    public record Payment(long id, String paymentNumber, Long invoiceId, String invoiceNumber, long customerId,
                          String customerName, BigDecimal amount, LocalDate paidAt, String method,
                          String reference, String cashAccountName) {
    }

    /** A credit note: the only way to correct an issued invoice. */
    public record CreditNote(long id, String creditNumber, long invoiceId, String invoiceNumber, long customerId,
                             String customerName, LocalDate creditDate, String reason, BigDecimal amountHt,
                             BigDecimal vatAmount, BigDecimal totalTtc, String status) {
    }
}
