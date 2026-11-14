package com.chemmanager.localization;

import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.chemmanager.config.UserPreferences;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;

/**
 * Central localisation service. Supports French, Arabic (RTL) and English at runtime:
 * changing the locale fires an observable event so the shell can retranslate itself
 * without restarting the application.
 */
public final class I18n {

    private static final String BUNDLE = "i18n.messages";
    /** Style class that turns on the larger Arabic typography of {@code app.css}. */
    public static final String ARABIC_STYLE_CLASS = "lang-ar";
    private static final I18n INSTANCE = new I18n();

    private final List<Locale> supported = List.of(
            Locale.forLanguageTag("fr"),
            Locale.forLanguageTag("ar"),
            Locale.forLanguageTag("en"));

    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>();
    private final ReadOnlyBooleanWrapper rtl = new ReadOnlyBooleanWrapper(false);
    private ResourceBundle bundle;

    private I18n() {
        Locale initial = UserPreferences.get().locale();
        if (!supported.contains(initial)) {
            initial = supported.get(0);
        }
        applyLocale(initial);
    }

    public static I18n get() {
        return INSTANCE;
    }

    public List<Locale> supportedLocales() {
        return supported;
    }

    public ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    public Locale locale() {
        return locale.get();
    }

    public ReadOnlyBooleanProperty rtlProperty() {
        return rtl.getReadOnlyProperty();
    }

    public boolean isRtl() {
        return rtl.get();
    }

    public void setLocale(Locale newLocale) {
        if (newLocale == null || newLocale.equals(locale.get())) {
            return;
        }
        if (!supported.contains(newLocale)) {
            throw new IllegalArgumentException("Unsupported locale: " + newLocale);
        }
        applyLocale(newLocale);
        UserPreferences.get().setLocale(newLocale);
        locale.set(newLocale); // notified last so listeners already see the new bundle
    }

    /**
     * Applies the active locale to a scene root: Arabic mirrors the layout (right to left) and
     * switches on the larger Arabic typography of {@code app.css}; the other languages reset both.
     * Called on the login root and on the shell root, and again whenever the user switches language.
     */
    public void applyTo(Parent root) {
        if (root == null) {
            return;
        }
        boolean arabic = isRtl();
        root.setNodeOrientation(arabic ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        if (arabic) {
            if (!root.getStyleClass().contains(ARABIC_STYLE_CLASS)) {
                root.getStyleClass().add(ARABIC_STYLE_CLASS);
            }
        } else {
            root.getStyleClass().remove(ARABIC_STYLE_CLASS);
        }
    }

    private void applyLocale(Locale newLocale) {
        this.bundle = ResourceBundle.getBundle(BUNDLE, newLocale);
        boolean isRtl = "ar".equals(newLocale.getLanguage()) || "he".equals(newLocale.getLanguage());
        rtl.set(isRtl);
        if (this.locale.get() != null) {
            this.locale.set(newLocale);
        } else {
            // initialise without notifying (property has no listeners yet)
            this.locale.set(newLocale);
        }
    }

    /** Translates a key; missing keys are returned wrapped in exclamation marks to be visible. */
    public String t(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            return "!" + key + "!";
        }
    }

    public String t(String key, Object... args) {
        String pattern = t(key);
        if (args == null || args.length == 0) {
            return pattern;
        }
        // MessageFormat treats a single quote as the start of a quoted section, which would
        // swallow the apostrophes of French and Arabic text; escape them before formatting.
        String escaped = pattern.replace("'", "''");
        return new MessageFormat(escaped, bundle.getLocale()).format(args);
    }

    /** Locale-aware formatter used by {@code util.Formatters}. */
    public Locale formattingLocale() {
        Locale base = bundle.getLocale();
        if ("ar".equals(base.getLanguage())) {
            // keep Latin digits: Algerian business documents mostly use Western numerals
            return Locale.forLanguageTag("ar-DZ-u-nu-latn");
        }
        return base;
    }

    public String displayName(Locale other) {
        String key = "language." + other.getLanguage();
        String translated = t(key);
        if (translated.startsWith("!")) {
            return other.getDisplayLanguage(other);
        }
        return translated;
    }
}
