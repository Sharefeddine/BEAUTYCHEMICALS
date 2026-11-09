package com.chemmanager.service.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.CompanySettings;
import com.chemmanager.service.SettingsService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Company identity served by {@code GET /api/settings} (settings + company in one payload).
 *
 * <p>Saving writes the company block to {@code PUT /api/settings/company} and the two application
 * preferences to {@code PUT /api/settings}, so the numbering prefixes and the database stay in
 * sync with what the user sees.</p>
 */
public final class ApiSettingsService implements SettingsService {

    private final ApiClient client;

    public ApiSettingsService(ApiClient client) {
        this.client = client;
    }

    @Override
    public CompanySettings load() {
        JsonNode payload = client.get(ApiEndpoints.SETTINGS, JsonNode.class);
        if (payload == null) {
            return CompanySettings.defaults();
        }
        return ApiMapper.company(payload.path("company"), payload.path("settings"));
    }

    @Override
    public void save(CompanySettings settings) {
        Map<String, Object> company = new LinkedHashMap<>();
        company.put("name", settings.companyName());
        company.put("legalName", settings.legalName());
        company.put("activity", settings.activity());
        company.put("address", settings.address());
        company.put("city", settings.city());
        company.put("wilaya", settings.wilaya());
        company.put("postalCode", settings.postalCode());
        company.put("phone", settings.phone());
        company.put("email", settings.email());
        company.put("website", settings.website());
        company.put("taxId", settings.taxId());
        company.put("tradeRegister", settings.rc());
        company.put("nif", settings.nif());
        company.put("taxArticle", settings.ai());
        company.put("vatRate", settings.defaultVatRate());
        company.put("invoicePrefix", settings.invoicePrefix());
        company.put("logoPath", settings.logoPath());
        client.putVoid(ApiEndpoints.COMPANY, company);

        Map<String, Object> preferences = new LinkedHashMap<>();
        preferences.put("app.defaultLocale", settings.localeCode());
        preferences.put("app.defaultTheme", settings.theme());
        client.putVoid(ApiEndpoints.SETTINGS, Map.of("settings", preferences));
    }
}
