package com.chemmanager.validation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.chemmanager.localization.I18n;
import com.chemmanager.util.Formatters;

/**
 * Fluent field validator used by every form. Messages are i18n keys so validation errors
 * switch language together with the rest of the interface.
 */
public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()-]{6,20}$");
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{2,24}$");

    private final Map<String, String> errors = new LinkedHashMap<>();

    private Validator() {
    }

    public static Validator create() {
        return new Validator();
    }

    public Validator required(String field, String value) {
        if (value == null || value.isBlank()) {
            fail(field, "validation.required");
        }
        return this;
    }

    public Validator maxLength(String field, String value, int max) {
        if (value != null && value.length() > max) {
            fail(field, "validation.max_length", max);
        }
        return this;
    }

    public Validator minLength(String field, String value, int min) {
        if (value != null && !value.isBlank() && value.length() < min) {
            fail(field, "validation.min_length", min);
        }
        return this;
    }

    public Validator email(String field, String value) {
        if (value != null && !value.isBlank() && !EMAIL.matcher(value).matches()) {
            fail(field, "validation.email");
        }
        return this;
    }

    public Validator phone(String field, String value) {
        if (value != null && !value.isBlank() && !PHONE.matcher(value).matches()) {
            fail(field, "validation.phone");
        }
        return this;
    }

    public Validator code(String field, String value) {
        if (value != null && !value.isBlank() && !SKU.matcher(value.toUpperCase()).matches()) {
            fail(field, "validation.code");
        }
        return this;
    }

    public Validator decimal(String field, String value) {
        if (value == null || value.isBlank()) {
            return this;
        }
        String normalized = value.replace('\u00A0', ' ').replace(" ", "").replace(",", ".");
        try {
            new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            fail(field, "validation.number");
        }
        return this;
    }

    public Validator positive(String field, String value) {
        if (value == null || value.isBlank()) {
            return this;
        }
        BigDecimal parsed = Formatters.parseDecimal(value);
        if (parsed.signum() <= 0) {
            fail(field, "validation.positive");
        }
        return this;
    }

    public Validator integer(String field, String value) {
        if (value == null || value.isBlank()) {
            return this;
        }
        try {
            Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            fail(field, "validation.integer");
        }
        return this;
    }

    public Validator between(String field, String value, BigDecimal min, BigDecimal max) {
        if (value == null || value.isBlank()) {
            return this;
        }
        BigDecimal parsed = Formatters.parseDecimal(value);
        if (parsed.compareTo(min) < 0 || parsed.compareTo(max) > 0) {
            fail(field, "validation.between", min.toPlainString(), max.toPlainString());
        }
        return this;
    }

    public Validator passwordsMatch(String passwordField, String password, String confirmation) {
        if (password != null && !password.equals(confirmation)) {
            fail(passwordField, "validation.password_mismatch");
        }
        return this;
    }

    public Validator custom(String field, boolean condition, String messageKey, Object... args) {
        if (!condition) {
            fail(field, messageKey, args);
        }
        return this;
    }

    public ValidationResult result() {
        return new ValidationResult(errors);
    }

    public void throwIfInvalid() {
        ValidationResult result = result();
        if (!result.isValid()) {
            throw new ValidationException(result);
        }
    }

    private void fail(String field, String messageKey, Object... args) {
        errors.putIfAbsent(field, I18n.get().t(messageKey, args));
    }
}
