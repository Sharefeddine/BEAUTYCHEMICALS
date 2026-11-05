package com.chemmanager.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Inclusive date interval used by every filtered screen and report. */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
    }

    public static DateRange of(LocalDate from, LocalDate to) {
        return new DateRange(from, to);
    }

    public static DateRange ofMonth(LocalDate reference) {
        return new DateRange(reference.withDayOfMonth(1), reference.withDayOfMonth(reference.lengthOfMonth()));
    }

    public static DateRange singleDay(LocalDate day) {
        return new DateRange(day, day);
    }

    public boolean contains(LocalDate date) {
        return date != null && !date.isBefore(from) && !date.isAfter(to);
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    public int monthCount() {
        return (int) (ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1);
    }

    public boolean overlaps(DateRange other) {
        return !(other.to.isBefore(from) || other.from.isAfter(to));
    }

    public DateRange withFrom(LocalDate newFrom) {
        return new DateRange(newFrom, to);
    }

    public DateRange withTo(LocalDate newTo) {
        return new DateRange(from, newTo);
    }
}
