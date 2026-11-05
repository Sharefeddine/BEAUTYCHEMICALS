package com.chemmanager.repository;

import com.chemmanager.model.CompanySettings;

/** Persistence contract for company settings (local preferences or API). */
public interface SettingsRepository {

    CompanySettings load();

    void save(CompanySettings settings);
}
