package com.chemmanager.model.safety;

/** An incident as it is reported on the workshop floor. */
public record NewIncident(Long materialId, String incidentType, String severity, String description,
                          String actionsTaken) {
}
