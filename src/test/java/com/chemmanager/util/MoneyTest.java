package com.chemmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Money helpers must always round half-up to two decimals and never produce floating drift. */
class MoneyTest {

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertEquals(new BigDecimal("12.35"), Money.round(new BigDecimal("12.345")));
        assertEquals(new BigDecimal("10.00"), Money.of(10));
        assertEquals(new BigDecimal("19.99"), Money.of("19,99"));
    }

    @Test
    void multipliesQuantityByPriceWithoutDrift() {
        BigDecimal total = Money.multiply(new BigDecimal("3"), new BigDecimal("0.1"));
        assertEquals(new BigDecimal("0.30"), total);
        assertEquals(new BigDecimal("0.30"), Money.sum(new BigDecimal("0.10"), new BigDecimal("0.20")));
    }

    @Test
    void vatIsComputedOnTheNetAmount() {
        assertEquals(new BigDecimal("19.00"), Money.vatShare(new BigDecimal("100"), new BigDecimal("19")));
        assertEquals(new BigDecimal("3.80"), Money.vatShare(new BigDecimal("20"), new BigDecimal("19")));
    }

    @Test
    void nullIsTreatedAsZero() {
        assertEquals(new BigDecimal("0.00"), Money.round(null));
        assertEquals(new BigDecimal("5.00"), Money.sum(null, new BigDecimal("5")));
    }

    @Test
    void currencyFormattingIsLocaleAwareAndNonEmpty() {
        String formatted = Money.format(new BigDecimal("1234.50"));
        assertFalse(formatted.isBlank());
        // the digits are always rendered, whatever the locale's separators and currency symbol
        assertTrue(formatted.replaceAll("\\D", "").contains("123450"), formatted);
    }
}
