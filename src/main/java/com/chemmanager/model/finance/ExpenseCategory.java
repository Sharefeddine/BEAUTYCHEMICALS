package com.chemmanager.model.finance;

/** A spending category; the salary and vehicle flags drive what the expense form offers. */
public record ExpenseCategory(long id, String code, String nameFr, String nameAr, boolean salary,
                              boolean vehicle, boolean active) {
}
