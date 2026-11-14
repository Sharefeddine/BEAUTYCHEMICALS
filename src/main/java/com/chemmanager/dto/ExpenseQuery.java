package com.chemmanager.dto;

import java.time.LocalDate;

/** The expense filter: search, category, status, window and paging. */
public record ExpenseQuery(String search, int page, int pageSize, long categoryId, String status,
                           LocalDate from, LocalDate to) {

    public static ExpenseQuery initial(int pageSize) {
        return new ExpenseQuery("", 0, pageSize, 0, "", null, null);
    }

    public ExpenseQuery withSearch(String value) {
        return new ExpenseQuery(value, page, pageSize, categoryId, status, from, to);
    }

    public ExpenseQuery withPage(int value) {
        return new ExpenseQuery(search, value, pageSize, categoryId, status, from, to);
    }

    public ExpenseQuery withPageSize(int value) {
        return new ExpenseQuery(search, page, value, categoryId, status, from, to);
    }

    public ExpenseQuery withCategory(long value) {
        return new ExpenseQuery(search, 0, pageSize, value, status, from, to);
    }

    public ExpenseQuery withStatus(String value) {
        return new ExpenseQuery(search, 0, pageSize, categoryId, value == null ? "" : value, from, to);
    }
}
