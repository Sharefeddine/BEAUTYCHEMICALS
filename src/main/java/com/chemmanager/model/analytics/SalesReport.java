package com.chemmanager.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What was invoiced in the window: the totals, then the same money per month, customer and product. */
public record SalesReport(LocalDate from, LocalDate to, Totals totals, List<Month> byMonth,
                          List<Customer> byCustomer, List<Product> byProduct) {

    public record Totals(int invoiceCount, BigDecimal amountHt, BigDecimal vatAmount, BigDecimal totalTtc,
                         BigDecimal paidAmount, BigDecimal outstanding) {
    }

    public record Month(String period, int invoiceCount, BigDecimal amountHt, BigDecimal totalTtc) {
    }

    public record Customer(long customerId, String name, int invoiceCount, BigDecimal amountHt,
                           BigDecimal totalTtc, BigDecimal outstanding) {
    }

    public record Product(long productId, String sku, String name, BigDecimal quantity, BigDecimal revenue) {
    }
}
