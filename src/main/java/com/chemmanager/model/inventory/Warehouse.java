package com.chemmanager.model.inventory;

/** A storage location. Quarantine warehouses only ever hold goods that are not yet released. */
public record Warehouse(long id, String code, String name, String address, boolean quarantine) {
}
