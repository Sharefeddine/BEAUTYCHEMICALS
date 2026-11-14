package com.chemmanager.service;

import java.math.BigDecimal;
import java.util.List;

import com.chemmanager.dto.MovementQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.StockQuery;
import com.chemmanager.model.inventory.InventorySummary;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.model.inventory.StockMovement;
import com.chemmanager.model.inventory.Warehouse;

/**
 * Batches, the stock ledger, and the two operations that change stock outside of a reception:
 * a physical count and a transfer between warehouses. Both are refused by the server when they
 * would break a quarantine rule or when the counted quantity matches the system one.
 */
public interface InventoryService {

    List<Warehouse> warehouses();

    InventorySummary summary();

    PagedResult<StockBatch> stock(StockQuery query);

    PagedResult<StockMovement> movements(MovementQuery query);

    void adjust(StockBatch batch, BigDecimal countedQuantity, String reason);

    void transfer(StockBatch batch, long targetWarehouseId, BigDecimal quantity, String reason);
}
