package com.chemmanager.repository;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.chemmanager.model.CompanySettings;

/**
 * Stores company settings in the local preferences store when the desktop client runs in
 * demo mode (no backend). Values survive restarts and remain per-machine.
 */
public final class PreferencesSettingsRepository implements SettingsRepository {

    private static final String KEY = "company.settings";
    private final Preferences prefs = Preferences.userNodeForPackage(PreferencesSettingsRepository.class);

    @Override
    public CompanySettings load() {
        String raw = prefs.get(KEY, null);
        if (raw == null || raw.isBlank()) {
            return CompanySettings.defaults();
        }
        String[] parts = new String(Base64.getDecoder().decode(raw)).split("\u001f", -1);
        if (parts.length < 20) {
            return CompanySettings.defaults();
        }
        return new CompanySettings(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5], parts[6],
                parts[7], parts[8], parts[9], parts[10], parts[11], parts[12], parts[13], parts[14],
                new BigDecimal(parts[15].isEmpty() ? "19" : parts[15]), parts[16], parts[17].isEmpty() ? null : parts[17],
                parts[18], parts[19]);
    }

    @Override
    public void save(CompanySettings settings) {
        String joined = String.join("\u001f",
                nz(settings.companyName()), nz(settings.legalName()), nz(settings.activity()),
                nz(settings.address()), nz(settings.city()), nz(settings.wilaya()),
                nz(settings.postalCode()), nz(settings.phone()), nz(settings.email()),
                nz(settings.website()), nz(settings.taxId()), nz(settings.rc()), nz(settings.nif()),
                nz(settings.ai()), nz(settings.currency()), settings.defaultVatRate().toPlainString(),
                nz(settings.invoicePrefix()), nz(settings.logoPath()), nz(settings.localeCode()),
                nz(settings.theme()));
        prefs.put(KEY, Base64.getEncoder().encodeToString(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        try {
            prefs.flush();
        } catch (BackingStoreException ex) {
            throw new IllegalStateException("Unable to persist company settings", ex);
        }
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
