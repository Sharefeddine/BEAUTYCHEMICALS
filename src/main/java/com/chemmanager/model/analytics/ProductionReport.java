package com.chemmanager.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** What the workshop planned, produced and wasted in the window. */
public record ProductionReport(LocalDate from, LocalDate to, Totals totals, List<Status> byStatus,
                               List<Product> byProduct) {

    public record Totals(int orderCount, BigDecimal plannedQuantity, BigDecimal actualQuantity,
                         BigDecimal wasteQuantity, BigDecimal totalCost, BigDecimal yieldPct,
                         BigDecimal unitCost) {
    }

    public record Status(String status, int orderCount, BigDecimal actualQuantity) {
    }

    public record Product(String sku, String name, int orderCount, BigDecimal plannedQuantity,
                          BigDecimal actualQuantity, BigDecimal wasteQuantity, BigDecimal totalCost) {
    }
}
