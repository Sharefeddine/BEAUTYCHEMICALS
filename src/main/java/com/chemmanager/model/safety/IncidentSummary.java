package com.chemmanager.model.safety;

/** Counters of the incident register, computed by the server over the current filter. */
public record IncidentSummary(int total, int open, int severe) {
}
