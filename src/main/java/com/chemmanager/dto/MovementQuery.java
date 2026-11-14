package com.chemmanager.dto;

/** Filters of the stock ledger: batch, warehouse and movement type. */
public record MovementQuery(String kind, Long batchId, Long warehouseId, String movementType, int page,
                            int pageSize) {

    public static MovementQuery initial(int pageSize) {
        return new MovementQuery("", null, null, "", 0, pageSize);
    }

    public MovementQuery withKind(String value) {
        return new MovementQuery(value, batchId, warehouseId, movementType, 0, pageSize);
    }

    public MovementQuery withBatch(Long value, String kindValue) {
        return new MovementQuery(kindValue, value, warehouseId, movementType, 0, pageSize);
    }

    public MovementQuery withWarehouse(Long value) {
        return new MovementQuery(kind, batchId, value, movementType, 0, pageSize);
    }

    public MovementQuery withType(String value) {
        return new MovementQuery(kind, batchId, warehouseId, value, 0, pageSize);
    }

    public MovementQuery withPage(int value) {
        return new MovementQuery(kind, batchId, warehouseId, movementType, value, pageSize);
    }

    public MovementQuery withPageSize(int value) {
        return new MovementQuery(kind, batchId, warehouseId, movementType, page, value);
    }
}
