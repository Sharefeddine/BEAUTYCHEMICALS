package com.chemmanager.model.analytics;

import java.math.BigDecimal;

/** Customer contribution row for the dashboard customer chart. */
public record CustomerPerformance(String customer, String wilaya, BigDecimal revenue,
                                  BigDecimal balance, int orders) {

    public CustomerPerformance {
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        balance = balance == null ? BigDecimal.ZERO : balance;
    }
}
