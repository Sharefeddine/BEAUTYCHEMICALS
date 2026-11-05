package com.chemmanager.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.chemmanager.localization.I18n;
import com.chemmanager.model.DateRange;

/** Range helpers shared by dashboard filters, reports and mock aggregations. */
public final class DateRanges {

    private DateRanges() {
    }

    public static DateRange lastMonths(int months, LocalDate today) {
        int span = Math.max(1, months);
        LocalDate start = today.minusMonths(span - 1L).withDayOfMonth(1);
        return new DateRange(start, today);
    }

    public static DateRange lastDays(int days, LocalDate today) {
        return new DateRange(today.minusDays(Math.max(1, days) - 1L), today);
    }

    public static List<LocalDate> monthStarts(DateRange range) {
        List<LocalDate> months = new ArrayList<>();
        LocalDate cursor = range.from().withDayOfMonth(1);
        LocalDate last = range.to().withDayOfMonth(1);
        while (!cursor.isAfter(last)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    public static long lengthInDays(DateRange range) {
        return ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
    }

    /** Localised human readable range description used in headers and exports. */
    public static String describe(DateRange range) {
        if (range == null) {
            return "";
        }
        if (range.from().equals(range.to())) {
            return Formatters.date(range.from());
        }
        return Formatters.date(range.from()) + " → " + Formatters.date(range.to())
                + " (" + I18n.get().t("dashboard.days_count", range.days()) + ")";
    }
}
