package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Margin by product: what each product sold, what it cost, and what it left. */
public record MarginReport(LocalDate from, LocalDate to, List<Line> lines, List<CategoryRow> byCategory,
                           BigDecimal revenue, BigDecimal cost, BigDecimal margin, BigDecimal marginPct) {

    public MarginReport {
        lines = lines == null ? List.of() : List.copyOf(lines);
        byCategory = byCategory == null ? List.of() : List.copyOf(byCategory);
    }

    public record Line(long productId, String sku, String name, String type, String categoryName,
                       BigDecimal quantity, BigDecimal revenue, BigDecimal cost, BigDecimal margin,
                       BigDecimal marginPct) {
    }

    public record CategoryRow(String categoryName, BigDecimal revenue, BigDecimal cost, BigDecimal margin) {
    }
}
