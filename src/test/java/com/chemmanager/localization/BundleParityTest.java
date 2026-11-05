package com.chemmanager.localization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * Guards the translation bundles: every supported language must expose exactly the keys of the
 * French reference bundle, with non-empty values and syntactically valid message patterns.
 */
class BundleParityTest {

    private static final List<Locale> LOCALES = List.of(
            Locale.forLanguageTag("fr"), Locale.forLanguageTag("ar"), Locale.forLanguageTag("en"));

    private ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("i18n.messages", locale);
    }

    private Set<String> keys(ResourceBundle bundle) {
        return new TreeSet<>(bundle.keySet());
    }

    @Test
    void everyLanguageExposesTheSameKeys() {
        Set<String> reference = keys(bundle(LOCALES.get(0)));
        assertFalse(reference.isEmpty(), "the French reference bundle must not be empty");
        for (Locale locale : LOCALES) {
            Set<String> actual = keys(bundle(locale));
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(actual);
            Set<String> extra = new TreeSet<>(actual);
            extra.removeAll(reference);
            assertTrue(missing.isEmpty(), locale + " is missing keys: " + missing);
            assertTrue(extra.isEmpty(), locale + " has unexpected keys: " + extra);
        }
    }

    @Test
    void noValueIsBlankOrAnUnresolvedKey() {
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = bundle(locale);
            List<String> blank = new ArrayList<>();
            for (String key : keys(bundle)) {
                String value = bundle.getString(key);
                if (value == null || value.isBlank()) {
                    blank.add(key);
                }
                assertFalse(value != null && value.startsWith("!") && value.endsWith("!"),
                        locale + " left key " + key + " unresolved");
            }
            assertTrue(blank.isEmpty(), locale + " has blank values: " + blank);
        }
    }

    @Test
    void everyValueIsAValidMessagePattern() {
        for (Locale locale : LOCALES) {
            ResourceBundle bundle = bundle(locale);
            for (String key : keys(bundle)) {
                String value = bundle.getString(key);
                try {
                    new MessageFormat(value, locale);
                } catch (IllegalArgumentException ex) {
                    throw new AssertionError("invalid message pattern for " + locale + "/" + key, ex);
                }
            }
        }
    }

    @Test
    void arabicBundleIsActuallyArabic() {
        ResourceBundle arabic = bundle(Locale.forLanguageTag("ar"));
        String dashboard = arabic.getString("nav.dashboard");
        assertNotNull(dashboard);
        assertTrue(dashboard.chars().anyMatch(ch -> ch >= 0x0600 && ch <= 0x06FF),
                "nav.dashboard should be translated into Arabic but was: " + dashboard);
    }

    @Test
    void rtlFlagFollowsTheLocale() {
        Locale previous = I18n.get().locale();
        try {
            I18n.get().setLocale(Locale.forLanguageTag("ar"));
            assertTrue(I18n.get().isRtl(), "Arabic must switch the interface to RTL");
            I18n.get().setLocale(Locale.forLanguageTag("fr"));
            assertFalse(I18n.get().isRtl(), "French must stay LTR");
            assertEquals("Tableau de bord", I18n.get().t("nav.dashboard"));
        } finally {
            I18n.get().setLocale(previous);
        }
    }
}
