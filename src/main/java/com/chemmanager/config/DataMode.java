package com.chemmanager.config;

/**
 * Determines where the application reads and writes its data.
 *
 * <p>{@link #MOCK} runs the client fully offline against deterministic demo data,
 * {@link #API} talks exclusively to the PHP REST backend and {@link #AUTO} probes the
 * backend on start-up and silently falls back to the demo dataset when it is unreachable.
 * Controllers are agnostic: they always talk to the service interfaces.</p>
 */
public enum DataMode {
    MOCK,
    API,
    AUTO;

    public static DataMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MOCK;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MOCK;
        }
    }
}
