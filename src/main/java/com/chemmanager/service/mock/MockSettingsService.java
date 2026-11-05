package com.chemmanager.service.mock;

import com.chemmanager.model.CompanySettings;
import com.chemmanager.repository.PreferencesSettingsRepository;
import com.chemmanager.repository.SettingsRepository;
import com.chemmanager.service.SettingsService;

/** Company settings persisted locally (demo mode). */
public final class MockSettingsService implements SettingsService {

    private final SettingsRepository repository = new PreferencesSettingsRepository();

    @Override
    public CompanySettings load() {
        return repository.load();
    }

    @Override
    public void save(CompanySettings settings) {
        repository.save(settings);
    }
}
