package com.chemmanager.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What was ordered and received in the window. */
public record PurchaseReport(LocalDate from, LocalDate to, Totals totals, List<Status> byStatus,
                             List<Month> byMonth, List<Supplier> bySupplier) {

    public record Totals(int orderCount, BigDecimal amountHt, BigDecimal vatAmount, BigDecimal totalTtc,
                         int receiptCount, BigDecimal receivedQuantity) {
    }

    public record Status(String status, int orderCount, BigDecimal totalTtc) {
    }

    public record Month(String period, int orderCount, BigDecimal totalTtc) {
    }

    public record Supplier(long supplierId, String name, int orderCount, BigDecimal totalTtc) {
    }
}
