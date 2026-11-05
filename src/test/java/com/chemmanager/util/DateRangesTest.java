package com.chemmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import com.chemmanager.model.DateRange;
import org.junit.jupiter.api.Test;

/** Date range helpers back every dashboard and report filter, so their boundaries are tested. */
class DateRangesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    void lastDaysIncludesBothBoundaries() {
        DateRange range = DateRanges.lastDays(7, TODAY);
        assertEquals(LocalDate.of(2026, 10, 30), range.from());
        assertEquals(TODAY, range.to());
        assertEquals(7, DateRanges.lengthInDays(range));
    }

    @Test
    void lastMonthsStartsOnTheFirstDayOfTheFirstMonth() {
        DateRange range = DateRanges.lastMonths(3, TODAY);
        assertEquals(LocalDate.of(2026, 9, 1), range.from());
        assertEquals(TODAY, range.to());
    }

    @Test
    void monthStartsListsEveryMonthInOrder() {
        List<LocalDate> months = DateRanges.monthStarts(new DateRange(LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 4, 2)));
        assertEquals(List.of(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)), months);
    }

    @Test
    void rangeContainsItsDates() {
        DateRange range = new DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        assertTrue(range.contains(LocalDate.of(2026, 11, 15)));
        assertTrue(range.contains(LocalDate.of(2026, 11, 1)));
        assertFalse(range.contains(LocalDate.of(2026, 12, 1)));
    }
}
