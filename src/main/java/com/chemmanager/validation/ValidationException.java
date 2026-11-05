package com.chemmanager.validation;

/** Thrown when invalid input reaches a service layer (defensive, the UI validates first). */
public class ValidationException extends RuntimeException {

    private final ValidationResult result;

    public ValidationException(ValidationResult result) {
        super(result == null ? "Validation failed" : result.firstError());
        this.result = result == null ? ValidationResult.valid() : result;
    }

    public ValidationResult result() {
        return result;
    }
}
