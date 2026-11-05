package com.chemmanager.service;

import com.chemmanager.model.CompanySettings;

/** Company-level configuration used by documents, taxes and numbering. */
public interface SettingsService {

    CompanySettings load();

    void save(CompanySettings settings);
}
