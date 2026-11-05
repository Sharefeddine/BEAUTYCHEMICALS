package com.chemmanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

/**
 * Immutable application configuration loaded from {@code /app.properties}.
 *
 * <p>Every key can be overridden by a JVM system property ({@code -Dapi.base.url=...})
 * which keeps secrets and per-machine values out of source control.</p>
 */
public final class AppConfig {

    private static final String RESOURCE = "/app.properties";
    private static volatile AppConfig instance;

    private final Properties properties;

    private AppConfig(Properties properties) {
        this.properties = properties;
    }

    public static AppConfig get() {
        AppConfig local = instance;
        if (local == null) {
            synchronized (AppConfig.class) {
                local = instance;
                if (local == null) {
                    local = new AppConfig(loadProperties());
                    instance = local;
                }
            }
        }
        return local;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            System.err.println("Unable to read " + RESOURCE + ": " + ex.getMessage());
        }
        // System properties always win over the packaged defaults.
        for (String key : props.stringPropertyNames()) {
            String override = System.getProperty(key);
            if (override != null && !override.isBlank()) {
                props.setProperty(key, override);
            }
        }
        String apiOverride = System.getenv("CHEMMANAGER_API_URL");
        if (apiOverride != null && !apiOverride.isBlank()) {
            props.setProperty("api.base.url", apiOverride);
        }
        String modeOverride = System.getenv("CHEMMANAGER_DATA_MODE");
        if (modeOverride != null && !modeOverride.isBlank()) {
            props.setProperty("data.mode", modeOverride);
        }
        return props;
    }

    public String get(String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public String appName() {
        return get("app.name", "ChemManager ERP");
    }

    public String appVersion() {
        return get("app.version", "1.0.0");
    }

    public String currencyCode() {
        return get("app.currency", "DZD");
    }

    public String apiBaseUrl() {
        return get("api.base.url", "http://localhost:8080/api");
    }

    public Duration apiTimeout() {
        return Duration.ofSeconds(getInt("api.timeout.seconds", 20));
    }

    public DataMode dataMode() {
        return DataMode.from(get("data.mode", "MOCK"));
    }

    public Locale defaultLocale() {
        String tag = get("app.default.locale", "fr");
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isBlank() ? Locale.FRENCH : locale;
    }

    public Theme defaultTheme() {
        return Theme.from(get("app.theme", "LIGHT"));
    }

    public boolean isDevelopment() {
        return "true".equalsIgnoreCase(get("app.development", "false"));
    }
}
