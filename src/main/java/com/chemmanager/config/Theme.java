package com.chemmanager.config;

/** Visual themes supported by the shell. */
public enum Theme {
    LIGHT("/css/theme-light.css", "theme.light"),
    DARK("/css/theme-dark.css", "theme.dark");

    private final String stylesheet;
    private final String labelKey;

    Theme(String stylesheet, String labelKey) {
        this.stylesheet = stylesheet;
        this.labelKey = labelKey;
    }

    public String stylesheet() {
        return stylesheet;
    }

    public String labelKey() {
        return labelKey;
    }

    public Theme toggle() {
        return this == LIGHT ? DARK : LIGHT;
    }

    public static Theme from(String raw) {
        if (raw != null && raw.trim().equalsIgnoreCase("DARK")) {
            return DARK;
        }
        return LIGHT;
    }
}
