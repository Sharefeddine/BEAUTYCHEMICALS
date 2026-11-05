package com.chemmanager.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Currency;
import java.util.Locale;

import com.chemmanager.config.AppConfig;
import com.chemmanager.localization.I18n;

/** Locale-aware formatting helpers shared by every screen and export. */
public final class Formatters {

    private Formatters() {
    }

    public static Locale locale() {
        return I18n.get().formattingLocale();
    }

    public static String currency(BigDecimal amount) {
        return currency(amount, AppConfig.get().currencyCode());
    }

    public static String currency(BigDecimal amount, String currencyCode) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        NumberFormat format = NumberFormat.getCurrencyInstance(locale());
        try {
            format.setCurrency(Currency.getInstance(currencyCode));
        } catch (IllegalArgumentException ex) {
            // unknown code: fall back to a plain number followed by the code
            return number(value) + " " + currencyCode;
        }
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    /** Condensed amount used inside KPI cards (e.g. 12,4 M DZD). */
    public static String compactCurrency(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        BigDecimal abs = value.abs();
        String suffix = "";
        BigDecimal scaled = value;
        if (abs.compareTo(BigDecimal.valueOf(1_000_000_000L)) >= 0) {
            scaled = value.divide(BigDecimal.valueOf(1_000_000_000L), 1, RoundingMode.HALF_UP);
            suffix = " Md";
        } else if (abs.compareTo(BigDecimal.valueOf(1_000_000L)) >= 0) {
            scaled = value.divide(BigDecimal.valueOf(1_000_000L), 1, RoundingMode.HALF_UP);
            suffix = " M";
        } else if (abs.compareTo(BigDecimal.valueOf(10_000L)) >= 0) {
            scaled = value.divide(BigDecimal.valueOf(1_000L), 1, RoundingMode.HALF_UP);
            suffix = " K";
        }
        return decimal(scaled, scaled.stripTrailingZeros().scale() > 1 ? 1 : 1) + suffix + " "
                + AppConfig.get().currencyCode();
    }

    public static String number(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale());
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    public static String number(long value) {
        return NumberFormat.getNumberInstance(locale()).format(value);
    }

    public static String decimal(BigDecimal value, int decimals) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale());
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            pattern.append("0".repeat(decimals));
        }
        return new DecimalFormat(pattern.toString(), symbols).format(value == null ? BigDecimal.ZERO : value);
    }

    public static String quantity(BigDecimal value, String unit) {
        String amount = value == null ? "0" : decimal(value, value.stripTrailingZeros().scale() > 0 ? 2 : 0);
        return unit == null || unit.isBlank() ? amount : amount + " " + unit;
    }

    public static String percent(BigDecimal value) {
        return decimal(value, 1) + " %";
    }

    public static String date(LocalDate date) {
        return date == null ? "—" : DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale()).format(date);
    }

    public static String shortDate(LocalDate date) {
        return date == null ? "—" : date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", locale()));
    }

    public static String month(LocalDate date) {
        return date == null ? "—" : date.format(DateTimeFormatter.ofPattern("MMM yy", locale()));
    }

    public static String monthLong(LocalDate date) {
        return date == null ? "—" : date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale()));
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale()));
    }

    public static String time(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm", locale()));
    }

    public static String dateTime(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", locale()));
    }

    /** Human friendly "3 h ago" style label used by the notification center. */
    public static String relative(Instant instant) {
        if (instant == null) {
            return "—";
        }
        Duration elapsed = Duration.between(instant, Instant.now());
        long minutes = Math.max(0, elapsed.toMinutes());
        if (minutes < 1) {
            return I18n.get().t("time.just_now");
        }
        if (minutes < 60) {
            return I18n.get().t("time.minutes_ago", minutes);
        }
        long hours = elapsed.toHours();
        if (hours < 24) {
            return I18n.get().t("time.hours_ago", hours);
        }
        long days = elapsed.toDays();
        if (days < 30) {
            return I18n.get().t("time.days_ago", days);
        }
        return shortDate(LocalDate.ofInstant(instant, ZoneId.systemDefault()));
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(locale());
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(locale());
    }

    /** Parses user input tolerating spaces, non-breaking spaces and comma decimals. */
    public static BigDecimal parseDecimal(String input) {
        if (input == null || input.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = input.replace('\u00A0', ' ').replace(" ", "").replace(",", ".").trim();
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
