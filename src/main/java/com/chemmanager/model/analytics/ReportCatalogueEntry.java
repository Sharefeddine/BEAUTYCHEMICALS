package com.chemmanager.model.analytics;

/**
 * One report the centre can produce.
 *
 * @param key      the report identifier the API and the screen share
 * @param group    the family the report belongs to: FINANCE, COMMERCE, OPERATIONS, SAFETY
 * @param periodic true when the report reads a date window
 */
public record ReportCatalogueEntry(String key, String group, boolean periodic) {
}
