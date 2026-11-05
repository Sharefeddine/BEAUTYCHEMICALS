package com.chemmanager.config;

import java.util.Locale;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Per-machine user preferences (theme, language, last used login, window state).
 * Backed by {@link Preferences} so settings survive restarts without touching the database.
 */
public final class UserPreferences {

    private static final UserPreferences INSTANCE = new UserPreferences();
    private final Preferences prefs = Preferences.userNodeForPackage(UserPreferences.class);

    private UserPreferences() {
    }

    public static UserPreferences get() {
        return INSTANCE;
    }

    public Theme theme() {
        return Theme.from(prefs.get("ui.theme", AppConfig.get().defaultTheme().name()));
    }

    public void setTheme(Theme theme) {
        prefs.put("ui.theme", theme.name());
        flush();
    }

    public Locale locale() {
        String tag = prefs.get("ui.locale", AppConfig.get().defaultLocale().toLanguageTag());
        Locale locale = Locale.forLanguageTag(tag);
        return locale.getLanguage().isBlank() ? AppConfig.get().defaultLocale() : locale;
    }

    public void setLocale(Locale locale) {
        prefs.put("ui.locale", locale.toLanguageTag());
        flush();
    }

    public boolean sidebarCollapsed() {
        return Boolean.parseBoolean(prefs.get("ui.sidebar.collapsed", "false"));
    }

    public void setSidebarCollapsed(boolean collapsed) {
        prefs.put("ui.sidebar.collapsed", Boolean.toString(collapsed));
        flush();
    }

    public String lastUsername() {
        return prefs.get("auth.last.username", "");
    }

    public void setLastUsername(String username) {
        prefs.put("auth.last.username", username == null ? "" : username);
        flush();
    }

    public boolean rememberUsername() {
        return Boolean.parseBoolean(prefs.get("auth.remember", "true"));
    }

    public void setRememberUsername(boolean remember) {
        prefs.put("auth.remember", Boolean.toString(remember));
        flush();
    }

    private void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            System.err.println("Unable to persist user preferences: " + ex.getMessage());
        }
    }
}
