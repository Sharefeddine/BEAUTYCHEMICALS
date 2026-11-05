package com.chemmanager.model;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/** The quick ranges offered by the dashboard date selector. */
public enum DateRangePreset {
    TODAY,
    YESTERDAY,
    LAST_7_DAYS,
    THIS_MONTH,
    LAST_MONTH,
    THIS_QUARTER,
    YEAR_TO_DATE,
    LAST_12_MONTHS,
    CUSTOM;

    public String labelKey() {
        return "range." + name().toLowerCase();
    }

    public DateRange resolve(LocalDate today) {
        return switch (this) {
            case TODAY -> DateRange.singleDay(today);
            case YESTERDAY -> DateRange.singleDay(today.minusDays(1));
            case LAST_7_DAYS -> new DateRange(today.minusDays(6), today);
            case THIS_MONTH -> DateRange.ofMonth(today);
            case LAST_MONTH -> DateRange.ofMonth(today.minusMonths(1));
            case THIS_QUARTER -> {
                LocalDate quarterStart = today.with(IsoFields.DAY_OF_QUARTER, 1);
                yield new DateRange(quarterStart, today);
            }
            case YEAR_TO_DATE -> new DateRange(today.withDayOfYear(1), today);
            case LAST_12_MONTHS -> new DateRange(today.minusMonths(11).withDayOfMonth(1), today);
            case CUSTOM -> new DateRange(today.minusDays(29), today);
        };
    }

    /** Presets that make sense for the dashboard selector (CUSTOM handled separately). */
    public static DateRangePreset[] selectorValues() {
        return new DateRangePreset[]{
                TODAY, YESTERDAY, LAST_7_DAYS, THIS_MONTH, LAST_MONTH, THIS_QUARTER,
                YEAR_TO_DATE, LAST_12_MONTHS, CUSTOM};
    }
}
