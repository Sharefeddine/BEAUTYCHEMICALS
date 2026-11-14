package com.chemmanager.dto;

import java.time.LocalDate;

/** The cash-book filter: search, direction, account, window and paging, as the table sends it. */
public record CashQuery(String search, int page, int pageSize, String direction, long cashAccountId,
                        LocalDate from, LocalDate to) {

    public static CashQuery initial(int pageSize) {
        return new CashQuery("", 0, pageSize, "", 0, null, null);
    }

    public CashQuery withSearch(String value) {
        return new CashQuery(value, page, pageSize, direction, cashAccountId, from, to);
    }

    public CashQuery withPage(int value) {
        return new CashQuery(search, value, pageSize, direction, cashAccountId, from, to);
    }

    public CashQuery withPageSize(int value) {
        return new CashQuery(search, page, value, direction, cashAccountId, from, to);
    }

    public CashQuery withDirection(String value) {
        return new CashQuery(search, 0, pageSize, value == null ? "" : value, cashAccountId, from, to);
    }

    public CashQuery withAccount(long value) {
        return new CashQuery(search, 0, pageSize, direction, value, from, to);
    }

    public CashQuery withWindow(LocalDate start, LocalDate end) {
        return new CashQuery(search, 0, pageSize, direction, cashAccountId, start, end);
    }
}
