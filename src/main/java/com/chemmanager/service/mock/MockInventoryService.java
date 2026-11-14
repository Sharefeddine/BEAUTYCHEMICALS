package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chemmanager.dto.MovementQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.StockQuery;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.inventory.InventorySummary;
import com.chemmanager.model.inventory.StockBatch;
import com.chemmanager.model.inventory.StockMovement;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.InventoryService;
import com.chemmanager.util.Paging;

/**
 * Inventory backed by the demo dataset.
 *
 * <p>The same rules as the server are applied so the offline demo behaves like the real thing:
 * quarantine walls, a refusal when a count finds no difference, and one ledger row per operation.</p>
 */
public final class MockInventoryService implements InventoryService {

    private static final List<Warehouse> WAREHOUSES = List.of(
            new Warehouse(1, "MP", "MP - Matieres premieres", "Zone industrielle, Setif", false),
            new Warehouse(2, "PF", "PF - Produits finis", "Zone industrielle, Setif", false),
            new Warehouse(3, "QUAR", "Quarantaine", "Zone industrielle, Setif", true));

    private final MockDataStore store = MockDataStore.get();
    private final List<StockBatch> batches = new ArrayList<>();
    private final List<StockMovement> movements = new ArrayList<>();
    private long batchSequence = 1;
    private long movementSequence = 1;

    public MockInventoryService() {
        seed();
    }

    @Override
    public List<Warehouse> warehouses() {
        return WAREHOUSES;
    }

    @Override
    public InventorySummary summary() {
        LocalDate limit = store.referenceDate().plusDays(60);
        BigDecimal materialValue = BigDecimal.ZERO;
        BigDecimal productValue = BigDecimal.ZERO;
        long materialCount = 0;
        long productCount = 0;
        long quarantine = 0;
        long expiring = 0;
        for (StockBatch batch : batches) {
            if (batch.isMaterial()) {
                materialCount++;
                materialValue = materialValue.add(batch.stockValue());
            } else {
                productCount++;
                productValue = productValue.add(batch.stockValue());
            }
            if (batch.isQuarantined()) {
                quarantine++;
            }
            if (batch.expiryDate() != null && batch.quantity().signum() > 0
                    && !batch.expiryDate().isAfter(limit)) {
                expiring++;
            }
        }
        long lowStock = store.rawMaterials().stream().filter(RawMaterial::isLowStock).count();
        return new InventorySummary(materialCount + productCount, materialCount, productCount,
                materialValue.add(productValue).setScale(2, RoundingMode.HALF_UP), quarantine, expiring,
                60, lowStock, WAREHOUSES.size(), "DZD");
    }

    @Override
    public PagedResult<StockBatch> stock(StockQuery query) {
        StockQuery effective = query == null ? StockQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().trim().toLowerCase(Locale.ROOT);
        LocalDate limit = effective.expiringDays() > 0 ? LocalDate.now().plusDays(effective.expiringDays()) : null;

        List<StockBatch> filtered = batches.stream()
                .filter(batch -> effective.warehouseId() == null || batch.warehouseId() == effective.warehouseId())
                .filter(batch -> blank(effective.kind()) || effective.kind().equalsIgnoreCase(batch.itemKind()))
                .filter(batch -> blank(effective.quality()) || effective.quality().equalsIgnoreCase(batch.qualityStatus()))
                .filter(batch -> !effective.availableOnly() || batch.quantity().signum() > 0)
                .filter(batch -> limit == null || (batch.expiryDate() != null && !batch.expiryDate().isAfter(limit)))
                .filter(batch -> search.isEmpty() || matches(batch, search))
                .sorted(byExpiry().thenComparing(StockBatch::itemName))
                .toList();

        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public PagedResult<StockMovement> movements(MovementQuery query) {
        MovementQuery effective = query == null ? MovementQuery.initial(25) : query;
        List<StockMovement> filtered = movements.stream()
                .filter(movement -> blank(effective.kind()) || effective.kind().equalsIgnoreCase(movement.itemKind()))
                .filter(movement -> blank(effective.movementType())
                        || effective.movementType().equalsIgnoreCase(movement.movementType()))
                .filter(movement -> effective.batchId() == null || movement.batchId() == effective.batchId())
                .filter(movement -> effective.warehouseId() == null || movement.warehouseId() == effective.warehouseId()
                        || (movement.targetWarehouseId() != null
                        && movement.targetWarehouseId().equals(effective.warehouseId())))
                .sorted(byDate())
                .toList();

        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public void adjust(StockBatch batch, BigDecimal countedQuantity, String reason) {
        requireReason(reason);
        StockBatch current = find(batch);
        BigDecimal counted = countedQuantity == null ? BigDecimal.ZERO : countedQuantity;
        BigDecimal delta = counted.subtract(current.quantity());
        if (delta.abs().compareTo(new BigDecimal("0.001")) < 0) {
            throw new IllegalStateException("The counted quantity matches the system quantity: nothing to adjust.");
        }
        replace(current, withQuantity(current, counted));
        record("ADJUSTMENT", current, current.warehouseId(), current.warehouseName(), null, null, delta.abs(),
                reason, "STOCK_COUNT");
    }

    @Override
    public void transfer(StockBatch batch, long targetWarehouseId, BigDecimal quantity, String reason) {
        requireReason(reason);
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalStateException("The quantity must be greater than zero.");
        }
        StockBatch current = find(batch);
        if (quantity.compareTo(current.quantity()) > 0) {
            throw new IllegalStateException("The batch holds " + current.quantity().toPlainString() + " "
                    + current.unit() + ": the transfer cannot exceed it.");
        }
        Warehouse target = WAREHOUSES.stream().filter(warehouse -> warehouse.id() == targetWarehouseId)
                .findFirst().orElseThrow(() -> new IllegalStateException("Warehouse not found."));
        if (target.id() == current.warehouseId()) {
            throw new IllegalStateException("The destination must be another warehouse.");
        }
        if (target.quarantine() && !current.isQuarantined()) {
            throw new IllegalStateException("Only goods held in quarantine may enter the quarantine warehouse.");
        }
        if (!target.quarantine() && !current.isReleased()) {
            throw new IllegalStateException("Only released goods may enter a selling warehouse: release the batch first.");
        }

        replace(current, withQuantity(current, current.quantity().subtract(quantity)));
        StockBatch existing = batches.stream()
                .filter(candidate -> candidate.itemKind().equals(current.itemKind())
                        && candidate.batchNumber().equals(current.batchNumber())
                        && candidate.warehouseId() == target.id())
                .findFirst().orElse(null);
        if (existing == null) {
            batches.add(new StockBatch(current.itemKind(), batchSequence++, current.itemId(), current.itemCode(),
                    current.itemName(), current.batchNumber(), target.id(), target.name(), quantity, current.unit(),
                    current.productionDate(), current.expiryDate(), current.daysToExpiry(), current.expired(),
                    current.qualityStatus(), current.unitCost(), value(quantity, current.unitCost()),
                    current.supplierName()));
        } else {
            replace(existing, withQuantity(existing, existing.quantity().add(quantity)));
        }
        record("TRANSFER", current, current.warehouseId(), current.warehouseName(), target.id(), target.name(),
                quantity, reason, "TRANSFER");
    }

    // ------------------------------------------------------------------ internals

    private void seed() {
        for (MockDataStore.BatchRecord row : store.batches()) {
            long warehouseId = row.warehouse().startsWith("PF") ? 2 : 1;
            String warehouseName = warehouseId == 2 ? WAREHOUSES.get(1).name() : WAREHOUSES.get(0).name();
            BigDecimal unitCost = unitCost(row);
            boolean expired = row.expiryDate() != null && row.expiryDate().isBefore(store.referenceDate());
            batches.add(new StockBatch(row.finishedProduct() ? "PRODUCT" : "MATERIAL", batchSequence++,
                    0, row.itemCode(), row.itemName(), row.batchNumber(), warehouseId, warehouseName,
                    row.quantity(), row.unit(), store.referenceDate().minusDays(30), row.expiryDate(),
                    days(row.expiryDate()), expired, "RELEASED", unitCost,
                    value(row.quantity(), unitCost), supplierName(row.itemCode())));
        }
        MockDataStore.BatchRecord quarantine = store.batches().stream().filter(row -> !row.finishedProduct())
                .findFirst().orElse(null);
        if (quarantine != null) {
            BigDecimal unitCost = unitCost(quarantine);
            batches.add(new StockBatch("MATERIAL", batchSequence++, 0, quarantine.itemCode(),
                    quarantine.itemName(), quarantine.batchNumber() + "-Q", 3, WAREHOUSES.get(2).name(),
                    new BigDecimal("25.000"), quarantine.unit(), store.referenceDate().minusDays(4),
                    quarantine.expiryDate(), days(quarantine.expiryDate()), false, "QUARANTINE", unitCost,
                    value(new BigDecimal("25.000"), unitCost), supplierName(quarantine.itemCode())));
        }
        for (StockBatch batch : new ArrayList<>(batches)) {
            record("IN", batch, batch.warehouseId(), batch.warehouseName(), null, null, batch.quantity(),
                    "Reception fournisseur", "GOODS_RECEIPT");
        }
    }

    private static Comparator<StockBatch> byExpiry() {
        return Comparator.comparing(StockBatch::expiryDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()));
    }

    private static Comparator<StockMovement> byDate() {
        Comparator<StockMovement> byOccurredAt = Comparator.comparing(StockMovement::occurredAt,
                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()));

        return byOccurredAt;
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A reason is required: the ledger must explain every movement.");
        }
    }

    private StockBatch find(StockBatch batch) {
        return batches.stream()
                .filter(candidate -> candidate.batchId() == batch.batchId()
                        && candidate.itemKind().equals(batch.itemKind()))
                .findFirst().orElseThrow(() -> new IllegalStateException("Batch not found."));
    }

    private void replace(StockBatch old, StockBatch updated) {
        int index = batches.indexOf(old);
        if (index >= 0) {
            batches.set(index, updated);
        }
    }

    private StockBatch withQuantity(StockBatch batch, BigDecimal quantity) {
        BigDecimal rounded = quantity.setScale(3, RoundingMode.HALF_UP);
        return new StockBatch(batch.itemKind(), batch.batchId(), batch.itemId(), batch.itemCode(), batch.itemName(),
                batch.batchNumber(), batch.warehouseId(), batch.warehouseName(), rounded, batch.unit(),
                batch.productionDate(), batch.expiryDate(), batch.daysToExpiry(), batch.expired(),
                batch.qualityStatus(), batch.unitCost(), value(rounded, batch.unitCost()), batch.supplierName());
    }

    private void record(String type, StockBatch batch, long warehouseId, String warehouseName,
                        Long targetWarehouseId, String targetWarehouseName, BigDecimal quantity, String reason,
                        String referenceType) {
        long id = movementSequence++;
        String number = "MVT-" + String.format("%05d", id);
        movements.add(new StockMovement(id, number, type, batch.itemKind(), batch.batchId(), batch.batchNumber(),
                batch.itemCode(), batch.itemName(), warehouseId, warehouseName, targetWarehouseId,
                targetWarehouseName, quantity, batch.unitCost(), value(quantity, batch.unitCost()),
                referenceType, reason, "demo", LocalDateTime.now()));
    }

    private String supplierName(String itemCode) {
        return store.rawMaterials().stream().filter(material -> material.code().equals(itemCode))
                .map(RawMaterial::supplierName).findFirst().orElse("");
    }

    private BigDecimal unitCost(MockDataStore.BatchRecord row) {
        return store.rawMaterials().stream().filter(material -> material.code().equals(row.itemCode()))
                .map(RawMaterial::purchasePrice).findFirst()
                .or(() -> store.products().stream().filter(product -> product.sku().equals(row.itemCode()))
                        .map(product -> product.purchaseCost()).findFirst())
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal value(BigDecimal quantity, BigDecimal unitCost) {
        BigDecimal cost = unitCost == null ? BigDecimal.ZERO : unitCost;
        return quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP);
    }

    private static Integer days(LocalDate expiry) {
        return expiry == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiry);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean matches(StockBatch batch, String search) {
        return batch.itemCode().toLowerCase(Locale.ROOT).contains(search)
                || batch.itemName().toLowerCase(Locale.ROOT).contains(search)
                || batch.batchNumber().toLowerCase(Locale.ROOT).contains(search);
    }
}
