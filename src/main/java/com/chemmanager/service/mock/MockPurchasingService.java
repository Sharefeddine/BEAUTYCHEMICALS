package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.PurchaseQuery;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.purchasing.GoodsReceipt;
import com.chemmanager.model.purchasing.PurchaseOrder;
import com.chemmanager.model.purchasing.PurchaseOrderDraft;
import com.chemmanager.model.purchasing.ReceptionDraft;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.PurchasingService;
import com.chemmanager.util.Paging;

/**
 * Purchase orders backed by the demo dataset.
 *
 * <p>In demo mode the stock lives in {@link MockInventoryService}, which is a separate dataset, so
 * a reception here advances the order and produces its receipt but does not move the demo stock.
 * With the PHP API both are the same transaction.</p>
 */
public final class MockPurchasingService implements PurchasingService {

    private static final BigDecimal VAT = new BigDecimal("19.00");
    private static final String[] WAREHOUSE_NAMES = {"", "MP - Matieres premieres", "PF - Produits finis",
            "Quarantaine"};

    private final MockDataStore store = MockDataStore.get();
    private final List<PurchaseOrder> orders = new ArrayList<>();
    private long orderSequence = 1;
    private long receiptSequence = 1;

    public MockPurchasingService() {
        seed();
    }

    @Override
    public PagedResult<PurchaseOrder> search(PurchaseQuery query) {
        PurchaseQuery effective = query == null ? PurchaseQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().trim().toLowerCase(Locale.ROOT);
        List<PurchaseOrder> filtered = orders.stream()
                .filter(order -> blank(effective.status()) || effective.status().equalsIgnoreCase(order.status()))
                .filter(order -> effective.supplierId() == null || order.supplierId() == effective.supplierId())
                .filter(order -> search.isEmpty() || matches(order, search))
                .sorted(Comparator.comparing(PurchaseOrder::orderDate).reversed()
                        .thenComparing(Comparator.comparing(PurchaseOrder::id).reversed()))
                .toList();

        return Paging.slice(filtered, effective.page(), effective.pageSize());
    }

    @Override
    public PurchaseOrder load(long id) {
        return orders.stream().filter(order -> order.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Purchase order not found."));
    }

    @Override
    public PurchaseOrder create(PurchaseOrderDraft draft) {
        if (draft == null || draft.lines().isEmpty()) {
            throw new IllegalStateException("An order needs at least one line.");
        }
        MockDataStore.SupplierRecord supplier = store.suppliers().stream()
                .filter(candidate -> candidate.id() == draft.supplierId()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Supplier not found."));

        List<PurchaseOrder.Line> items = new ArrayList<>();
        BigDecimal amountHt = BigDecimal.ZERO;
        long lineId = 1;
        for (PurchaseOrderDraft.Line line : draft.lines()) {
            RawMaterial material = store.rawMaterials().stream()
                    .filter(candidate -> candidate.id() == line.materialId()).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Material not found."));
            BigDecimal quantity = line.quantity() == null ? BigDecimal.ZERO : line.quantity();
            BigDecimal price = line.unitPrice() == null ? BigDecimal.ZERO : line.unitPrice();
            BigDecimal lineTotal = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            amountHt = amountHt.add(lineTotal);
            items.add(new PurchaseOrder.Line(lineId++, material.id(), material.code(),
                    material.commercialName(), quantity, material.storageUnit(), price,
                    line.vatRate() == null ? VAT : line.vatRate(), BigDecimal.ZERO, quantity, lineTotal));
        }
        BigDecimal vat = amountHt.multiply(VAT).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        long id = orderSequence++;
        PurchaseOrder order = new PurchaseOrder(id, number("BC", id), supplier.id(), supplier.name(), "DRAFT",
                LocalDate.now(), draft.expectedDate(), amountHt, vat, amountHt.add(vat), "DZD",
                draft.notes() == null ? "" : draft.notes(), items.size(), BigDecimal.ZERO, 0, items, List.of());
        orders.add(0, order);

        return order;
    }

    @Override
    public PurchaseOrder send(long id) {
        PurchaseOrder order = load(id);
        if (!order.isDraft()) {
            throw new IllegalStateException("Only a draft order can be sent to the supplier.");
        }

        return replace(order, "SENT", order.items(), order.receipts());
    }

    @Override
    public PurchaseOrder cancel(long id) {
        PurchaseOrder order = load(id);
        if ("CANCELLED".equalsIgnoreCase(order.status())) {
            throw new IllegalStateException("This order is already cancelled.");
        }
        if ("RECEIVED".equalsIgnoreCase(order.status())) {
            throw new IllegalStateException("A fully received order cannot be cancelled.");
        }
        if (order.receivedQuantity() != null && order.receivedQuantity().signum() > 0) {
            throw new IllegalStateException("Part of this order is already in stock: it cannot be cancelled.");
        }

        return replace(order, "CANCELLED", order.items(), order.receipts());
    }

    @Override
    public GoodsReceipt receive(long orderId, ReceptionDraft draft) {
        PurchaseOrder order = load(orderId);
        if (!order.canBeReceived()) {
            throw new IllegalStateException("Only a sent order can be received (current status: "
                    + order.status() + ").");
        }
        if (draft == null || draft.lines().isEmpty()) {
            throw new IllegalStateException("A reception needs at least one line.");
        }
        int warehouseId = (int) draft.warehouseId();
        if (warehouseId <= 0 || warehouseId >= WAREHOUSE_NAMES.length) {
            throw new IllegalStateException("Warehouse not found.");
        }
        if (draft.release() && warehouseId == 3) {
            throw new IllegalStateException("Released goods cannot be received in the quarantine warehouse.");
        }
        String quality = draft.release() ? "RELEASED" : "QUARANTINE";

        List<PurchaseOrder.Line> items = new ArrayList<>(order.items());
        List<GoodsReceipt.Line> receiptLines = new ArrayList<>();
        long receiptLineId = 1;
        for (ReceptionDraft.Line line : draft.lines()) {
            int index = indexOf(items, line.orderItemId());
            if (index < 0) {
                throw new IllegalStateException("The order line was not found.");
            }
            PurchaseOrder.Line ordered = items.get(index);
            BigDecimal quantity = line.quantity() == null ? BigDecimal.ZERO : line.quantity();
            if (quantity.signum() <= 0) {
                throw new IllegalStateException("The received quantity must be greater than zero.");
            }
            if (quantity.compareTo(ordered.remainingQty()) > 0) {
                throw new IllegalStateException("Only " + ordered.remainingQty().toPlainString() + " "
                        + ordered.unit() + " remain to be received on " + ordered.materialCode() + ".");
            }
            if (line.batchNumber() == null || line.batchNumber().isBlank()) {
                throw new IllegalStateException("A batch number is required for every received line.");
            }
            BigDecimal received = ordered.receivedQty().add(quantity);
            items.set(index, new PurchaseOrder.Line(ordered.id(), ordered.materialId(), ordered.materialCode(),
                    ordered.materialName(), ordered.quantity(), ordered.unit(), ordered.unitPrice(),
                    ordered.vatRate(), received, ordered.quantity().subtract(received), ordered.lineTotal()));
            receiptLines.add(new GoodsReceipt.Line(receiptLineId++, ordered.materialId(), ordered.materialCode(),
                    ordered.materialName(), quantity, ordered.unitPrice(), line.batchNumber().trim(),
                    line.expiryDate(), quantity, quality));
        }

        BigDecimal receivedTotal = items.stream().map(PurchaseOrder.Line::receivedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal orderedTotal = items.stream().map(PurchaseOrder.Line::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String status = receivedTotal.compareTo(orderedTotal) >= 0 ? "RECEIVED"
                : (receivedTotal.signum() > 0 ? "PARTIAL" : "SENT");

        long receiptId = receiptSequence++;
        PurchaseOrder.Receipt receipt = new PurchaseOrder.Receipt(receiptId, number("BR", receiptId),
                warehouseId, WAREHOUSE_NAMES[warehouseId], LocalDateTime.now(), "demo", receiptLines.size());
        List<PurchaseOrder.Receipt> receipts = new ArrayList<>(order.receipts());
        receipts.add(0, receipt);
        replace(order, status, items, receipts);

        return new GoodsReceipt(receiptId, receipt.receiptNumber(), orderId, order.orderNumber(), warehouseId,
                receipt.warehouseName(), receipt.receivedAt(), "demo", draft.notes() == null ? "" : draft.notes(),
                status, receiptLines);
    }

    // ------------------------------------------------------------------ internals

    private void seed() {
        List<MockDataStore.SupplierRecord> suppliers = store.suppliers();
        List<RawMaterial> materials = store.rawMaterials();
        if (suppliers.isEmpty() || materials.size() < 4) {
            return;
        }
        LocalDate today = store.referenceDate();
        addSeed(suppliers.get(0), today.minusDays(26), "SENT", materials, 0, 1);
        PurchaseOrder partial = addSeed(suppliers.get(1 % suppliers.size()), today.minusDays(19), "SENT",
                materials, 2, 3);
        receiveSeed(partial, new BigDecimal("0.50"), today.minusDays(12));
        PurchaseOrder received = addSeed(suppliers.get(0), today.minusDays(41), "SENT", materials, 1);
        receiveSeed(received, BigDecimal.ONE, today.minusDays(35));
        addSeed(suppliers.get(suppliers.size() - 1), today.minusDays(3), "DRAFT", materials, 4);
    }

    private PurchaseOrder addSeed(MockDataStore.SupplierRecord supplier, LocalDate orderDate, String status,
                                  List<RawMaterial> materials, int... materialIndexes) {
        List<PurchaseOrder.Line> items = new ArrayList<>();
        BigDecimal amountHt = BigDecimal.ZERO;
        long lineId = 1;
        for (int materialIndex : materialIndexes) {
            RawMaterial material = materials.get(materialIndex % materials.size());
            BigDecimal quantity = new BigDecimal(20L + 15L * materialIndex).setScale(3, RoundingMode.HALF_UP);
            BigDecimal price = material.purchasePrice() == null ? BigDecimal.ZERO : material.purchasePrice();
            BigDecimal lineTotal = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            amountHt = amountHt.add(lineTotal);
            items.add(new PurchaseOrder.Line(lineId++, material.id(), material.code(),
                    material.commercialName(), quantity, material.storageUnit(), price, VAT, BigDecimal.ZERO,
                    quantity, lineTotal));
        }
        BigDecimal vat = amountHt.multiply(VAT).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        long id = orderSequence++;
        PurchaseOrder order = new PurchaseOrder(id, number("BC", id), supplier.id(), supplier.name(), status,
                orderDate, orderDate.plusDays(10), amountHt, vat, amountHt.add(vat), "DZD", "", items.size(),
                BigDecimal.ZERO, 0, items, List.of());
        orders.add(0, order);

        return order;
    }

    private void receiveSeed(PurchaseOrder order, BigDecimal ratio, LocalDate receivedOn) {
        List<PurchaseOrder.Line> items = new ArrayList<>();
        List<GoodsReceipt.Line> lines = new ArrayList<>();
        long lineId = 1;
        for (PurchaseOrder.Line line : order.items()) {
            BigDecimal quantity = line.quantity().multiply(ratio).setScale(3, RoundingMode.HALF_UP);
            BigDecimal received = ratio.compareTo(BigDecimal.ONE) >= 0 ? line.quantity() : quantity;
            items.add(new PurchaseOrder.Line(line.id(), line.materialId(), line.materialCode(), line.materialName(),
                    line.quantity(), line.unit(), line.unitPrice(), line.vatRate(), received,
                    line.quantity().subtract(received), line.lineTotal()));
            lines.add(new GoodsReceipt.Line(lineId++, line.materialId(), line.materialCode(), line.materialName(),
                    quantity, line.unitPrice(), "B-" + receivedOn.toString().substring(2, 4)
                    + receivedOn.toString().substring(5, 7) + "-" + line.id(), receivedOn.plusMonths(10),
                    quantity, "RELEASED"));
        }
        long receiptId = receiptSequence++;
        PurchaseOrder.Receipt receipt = new PurchaseOrder.Receipt(receiptId, number("BR", receiptId), 1,
                WAREHOUSE_NAMES[1], receivedOn.atStartOfDay(), "demo", lines.size());
        String status = ratio.compareTo(BigDecimal.ONE) >= 0 ? "RECEIVED" : "PARTIAL";
        replace(order, status, items, List.of(receipt));
    }

    private PurchaseOrder replace(PurchaseOrder order, String status, List<PurchaseOrder.Line> items,
                                  List<PurchaseOrder.Receipt> receipts) {
        BigDecimal receivedQuantity = items.stream().map(PurchaseOrder.Line::receivedQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PurchaseOrder updated = new PurchaseOrder(order.id(), order.orderNumber(), order.supplierId(),
                order.supplierName(), status, order.orderDate(), order.expectedDate(), order.amountHt(),
                order.vatAmount(), order.totalTtc(), order.currency(), order.notes(), items.size(),
                receivedQuantity, receipts.size(), items, receipts);
        int index = orders.indexOf(order);
        if (index >= 0) {
            orders.set(index, updated);
        }

        return updated;
    }

    private static int indexOf(List<PurchaseOrder.Line> items, long lineId) {
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id() == lineId) {
                return index;
            }
        }
        return -1;
    }

    private static String number(String prefix, long sequence) {
        return prefix + "-" + String.format("%05d", sequence);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean matches(PurchaseOrder order, String search) {
        return order.orderNumber().toLowerCase(Locale.ROOT).contains(search)
                || order.supplierName().toLowerCase(Locale.ROOT).contains(search);
    }
}
