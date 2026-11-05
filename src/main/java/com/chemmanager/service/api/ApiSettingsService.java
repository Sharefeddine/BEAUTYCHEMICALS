package com.chemmanager.service.api;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.CompanySettings;
import com.chemmanager.service.SettingsService;

/** Company settings served by {@code /settings/company}. */
public final class ApiSettingsService implements SettingsService {

    private final ApiClient client;

    public ApiSettingsService(ApiClient client) {
        this.client = client;
    }

    @Override
    public CompanySettings load() {
        CompanySettings settings = client.get(ApiEndpoints.SETTINGS, CompanySettings.class);
        return settings == null ? CompanySettings.defaults() : settings;
    }

    @Override
    public void save(CompanySettings settings) {
        client.put(ApiEndpoints.SETTINGS, settings, CompanySettings.class);
    }
}
