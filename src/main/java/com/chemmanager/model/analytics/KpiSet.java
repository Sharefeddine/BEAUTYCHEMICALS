package com.chemmanager.model.analytics;

import java.math.BigDecimal;

/**
 * Every figure shown on the executive dashboard. All values are computed from stored
 * transactions (invoices, receipts, movements, expenses) — never hardcoded.
 */
public record KpiSet(
        BigDecimal salesToday,
        BigDecimal salesMonth,
        BigDecimal salesYear,
        BigDecimal salesInRange,
        BigDecimal purchasesInRange,
        BigDecimal expensesInRange,
        BigDecimal grossProfitInRange,
        BigDecimal netProfitInRange,
        BigDecimal inventoryValue,
        BigDecimal receivables,
        BigDecimal payables,
        BigDecimal cashBalance,
        BigDecimal productionQuantityInRange,
        BigDecimal averageBasket,
        int lowStockCount,
        int expiringCount,
        int pendingProductionCount,
        int openOrders) {

    public KpiSet {
        salesToday = nz(salesToday);
        salesMonth = nz(salesMonth);
        salesYear = nz(salesYear);
        salesInRange = nz(salesInRange);
        purchasesInRange = nz(purchasesInRange);
        expensesInRange = nz(expensesInRange);
        grossProfitInRange = nz(grossProfitInRange);
        netProfitInRange = nz(netProfitInRange);
        inventoryValue = nz(inventoryValue);
        receivables = nz(receivables);
        payables = nz(payables);
        cashBalance = nz(cashBalance);
        productionQuantityInRange = nz(productionQuantityInRange);
        averageBasket = nz(averageBasket);
    }

    public BigDecimal marginPercent() {
        if (salesInRange.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return grossProfitInRange.multiply(BigDecimal.valueOf(100)).divide(salesInRange, 1, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
