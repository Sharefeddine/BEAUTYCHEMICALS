package com.chemmanager.model;

/** Generic severity used by alerts, notifications and activity entries. */
public enum Severity {
    INFO,
    WARNING,
    CRITICAL;

    public String styleClass() {
        return switch (this) {
            case INFO -> "badge-info";
            case WARNING -> "badge-warning";
            case CRITICAL -> "badge-danger";
        };
    }
}
