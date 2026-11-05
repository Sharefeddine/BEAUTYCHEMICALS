package com.chemmanager.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Outcome of a validation run: field name to translated message. */
public final class ValidationResult {

    private final Map<String, String> errors;

    public ValidationResult(Map<String, String> errors) {
        this.errors = errors == null || errors.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(errors));
    }

    public static ValidationResult valid() {
        return new ValidationResult(Map.of());
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public Map<String, String> errors() {
        return errors;
    }

    public Optional<String> errorFor(String field) {
        return Optional.ofNullable(errors.get(field));
    }

    public String firstError() {
        return errors.values().stream().findFirst().orElse("");
    }
}
