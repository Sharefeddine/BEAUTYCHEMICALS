package com.chemmanager.dto;

import java.util.Map;

/** Error envelope returned by the PHP API: {@code {"error": {"code": ..., "message": ...}}}. */
public record ApiError(String code, String message, Map<String, String> details) {

    public ApiError {
        details = details == null ? Map.of() : Map.copyOf(details);
        code = code == null ? "error" : code;
        message = message == null || message.isBlank() ? code : message;
    }
}
