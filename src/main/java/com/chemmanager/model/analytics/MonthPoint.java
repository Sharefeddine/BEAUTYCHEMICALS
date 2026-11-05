package com.chemmanager.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Aggregated monthly figures used by the dashboard charts. */
public record MonthPoint(
        LocalDate month,
        BigDecimal sales,
        BigDecimal purchases,
        BigDecimal expenses,
        BigDecimal productionCost,
        BigDecimal productionQuantity) {

    public MonthPoint {
        Objects.requireNonNull(month, "month");
        sales = nz(sales);
        purchases = nz(purchases);
        expenses = nz(expenses);
        productionCost = nz(productionCost);
        productionQuantity = nz(productionQuantity);
    }

    public BigDecimal revenueMinusExpenses() {
        return sales.subtract(expenses);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
