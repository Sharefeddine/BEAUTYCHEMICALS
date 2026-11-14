package com.chemmanager.model.analytics;

import java.util.List;

/** How much of the chemical register is documented, and what the incident register holds. */
public record SafetyReport(Materials materials, Incidents incidents, List<MissingSheet> missingSheets) {

    public record Materials(int total, int hazardous, int withoutSheet, int obsoleteSheets) {
    }

    public record Incidents(List<Severity> bySeverity, List<Type> byType, int total) {
    }

    public record Severity(String severity, String status, int incidentCount) {
    }

    public record Type(String incidentType, int incidentCount) {
    }

    public record MissingSheet(String code, String commercialName, String hazardClass) {
    }
}
