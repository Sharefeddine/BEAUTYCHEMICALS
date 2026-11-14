package com.chemmanager.model.safety;

import java.time.LocalDateTime;

/** One entry of the incident register. */
public record SafetyIncident(long id, String incidentNumber, Long materialId, String materialName,
                             LocalDateTime incidentDate, String incidentType, String severity,
                             String description, String actionsTaken, String status,
                             LocalDateTime closedAt, String reportedByName) {

    public boolean isOpen() {
        return !"CLOSED".equals(status);
    }

    public boolean isSevere() {
        return "MAJOR".equals(severity) || "CRITICAL".equals(severity);
    }
}
