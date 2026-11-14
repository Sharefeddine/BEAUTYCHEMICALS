package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.util.List;

/** The result of a month: revenue, cost of goods, operating expenses, and what is left. */
public record IncomeStatement(String period, BigDecimal invoicedHt, BigDecimal creditNotesHt,
                              BigDecimal revenue, BigDecimal vatCollected, BigDecimal costOfGoods,
                              BigDecimal grossMargin, BigDecimal grossMarginPct, BigDecimal expenses,
                              BigDecimal result, BigDecimal resultPct, List<CategoryExpense> byCategory) {

    public IncomeStatement {
        byCategory = byCategory == null ? List.of() : List.copyOf(byCategory);
    }

    public record CategoryExpense(String code, String name, BigDecimal amount) {
    }
}
