package com.chemmanager.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monetary helpers: everything money-related is {@link BigDecimal} with 2 decimals. */
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(String value) {
        return Formatters.parseDecimal(value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal round(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return round(nz(left).multiply(nz(right)));
    }

    public static BigDecimal sum(BigDecimal... values) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            total = total.add(nz(value));
        }
        return round(total);
    }

    public static BigDecimal vatShare(BigDecimal netAmount, BigDecimal vatRate) {
        return round(nz(netAmount).multiply(nz(vatRate)).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    public static String format(BigDecimal amount) {
        return Formatters.currency(amount);
    }

    public static String compact(BigDecimal amount) {
        return Formatters.compactCurrency(amount);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
