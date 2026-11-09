package com.chemmanager.api;

import java.util.Map;

/** Transport or business error raised by the PHP REST API. */
public class ApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, String> details;

    public ApiException(int status, String code, String message, Map<String, String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ApiException(String message) {
        this(0, "client_error", message, java.util.Map.of());
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = 0;
        this.code = "network_error";
        this.details = Map.of();
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }

    public boolean isNetworkFailure() {
        return status == 0;
    }

    public boolean isUnauthorized() {
        return status == 401 || status == 403;
    }

    /** i18n key of a user-facing explanation. */
    public String messageKey() {
        if (isNetworkFailure()) {
            return "error.network";
        }
        return switch (status) {
            case 401 -> "error.unauthorized";
            case 403 -> "error.forbidden";
            case 404 -> "error.not_found";
            case 409 -> "error.conflict";
            case 422 -> "error.validation";
            default -> "error.server";
        };
    }
}
