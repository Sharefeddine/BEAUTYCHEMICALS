package com.chemmanager.model.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer order with the two progress figures the warehouse and the accountant need:
 * what was delivered and what was invoiced.
 *
 * <p>The statuses follow the chain: DRAFT, CONFIRMED, RESERVED (a delivery note is prepared),
 * PARTIAL (goods left, not fully billed), INVOICED, CANCELLED.</p>
 */
public record SalesOrder(
        long id,
        String orderNumber,
        long customerId,
        String customerName,
        String customerCode,
        String status,
        LocalDate orderDate,
        LocalDate deliveryDate,
        BigDecimal amountHt,
        BigDecimal vatAmount,
        BigDecimal totalTtc,
        BigDecimal orderedQty,
        BigDecimal deliveredQty,
        BigDecimal invoicedQty,
        double deliveryProgress,
        double invoiceProgress,
        String notes,
        List<Line> items,
        List<Ref> deliveries,
        List<Ref> invoices) {

    public SalesOrder {
        items = items == null ? List.of() : List.copyOf(items);
        deliveries = deliveries == null ? List.of() : List.copyOf(deliveries);
        invoices = invoices == null ? List.of() : List.copyOf(invoices);
        orderedQty = orderedQty == null ? BigDecimal.ZERO : orderedQty;
        deliveredQty = deliveredQty == null ? BigDecimal.ZERO : deliveredQty;
        invoicedQty = invoicedQty == null ? BigDecimal.ZERO : invoicedQty;
        totalTtc = totalTtc == null ? BigDecimal.ZERO : totalTtc;
    }

    public boolean confirmable() {
        return "DRAFT".equals(status);
    }

    public boolean deliverable() {
        return "CONFIRMED".equals(status) || "RESERVED".equals(status) || "PARTIAL".equals(status);
    }

    public boolean invoicable() {
        return "PARTIAL".equals(status) || "RESERVED".equals(status);
    }

    public boolean cancellable() {
        return !"INVOICED".equals(status) && !"CANCELLED".equals(status)
                && (deliveries.isEmpty() || deliveries.stream().allMatch(ref -> "RETURNED".equals(ref.status())));
    }

    /** What is still to ship on a line. */
    public record Line(long id, long productId, String productCode, String productName, String unit,
                       BigDecimal quantity, BigDecimal deliveredQty, BigDecimal invoicedQty, BigDecimal unitPrice,
                       BigDecimal discountPct, BigDecimal vatRate, BigDecimal lineTotal) {

        public BigDecimal remaining() {
            BigDecimal quantityValue = quantity == null ? BigDecimal.ZERO : quantity;
            BigDecimal deliveredValue = deliveredQty == null ? BigDecimal.ZERO : deliveredQty;
            return quantityValue.subtract(deliveredValue);
        }
    }

    /** A delivery note or an invoice attached to the order, with the bits the detail panel shows. */
    public record Ref(long id, String number, String status, LocalDate date, BigDecimal total) {
    }

    /** A line the user typed in the order dialog. */
    public record NewLine(long productId, BigDecimal quantity, BigDecimal discountPct) {
    }

    /** The order request: the customer, the lines, and the date the customer expects the goods. */
    public record NewOrder(long customerId, List<NewLine> items, LocalDate deliveryDate, String notes) {

        public NewOrder {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
