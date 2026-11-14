package com.chemmanager.localization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

import com.chemmanager.util.Formatters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Numbers and dates inside Arabic text must keep reading left to right.
 *
 * <p>Without an isolate, a right-to-left paragraph reorders "24,8 M DZD" into "M DZD 24,8", which
 * reads like a different figure on a purchase order. {@code Formatters} wraps figures in U+2066 …
 * U+2069 (invisible in the interface) exactly when the active locale is Arabic, and leaves French
 * and English untouched.</p>
 */
class RtlFormattingTest {

    private Locale previous;

    @BeforeEach
    void remember() {
        previous = I18n.get().locale();
    }

    @AfterEach
    void restore() {
        I18n.get().setLocale(previous);
    }

    @Test
    @DisplayName("French figures are not wrapped in an isolate")
    void latinFiguresStayPlain() {
        I18n.get().setLocale(Locale.FRENCH);
        String amount = Formatters.currency(new BigDecimal("1234.50"));
        assertFalse(amount.contains("\u2066"), "Latin text must not carry bidi control characters: " + amount);
        assertTrue(amount.contains("234"), "the amount must still be formatted: " + amount);
    }

    @Test
    @DisplayName("Arabic amounts and dates are isolated and keep Latin digits")
    void arabicFiguresAreIsolated() {
        I18n.get().setLocale(Locale.forLanguageTag("ar"));
        String amount = Formatters.currency(new BigDecimal("1234.50"));
        assertTrue(amount.startsWith("\u2066") && amount.endsWith("\u2069"),
                "an Arabic amount must be embedded left to right: " + amount);
        assertTrue(amount.contains("234"), "the digits must stay Latin and in order: " + amount);

        String date = Formatters.shortDate(LocalDate.of(2026, 11, 14));
        assertTrue(date.startsWith("\u2066") && date.endsWith("\u2069"),
                "an Arabic date must be embedded left to right: " + date);
        assertTrue(date.contains("14/11/2026"), "dd/MM/yyyy must survive the isolation: " + date);
    }
}
