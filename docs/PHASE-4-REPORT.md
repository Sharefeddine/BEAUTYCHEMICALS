# Phase 4 — Inventory, warehouses and purchasing

> Scope: batch-level stock, FEFO allocation, the append-only stock ledger, physical counts,
> inter-warehouse transfers, suppliers and the full purchase cycle up to goods reception — on both
> halves (PHP API + JavaFX desktop), verified in CI against a real MySQL 8 database.

**Guiding rule:** stock is never a number stored on a product. It is the sum of the batch rows, and
a batch only ever changes through `stockMovements`. That single decision is what makes the rest of
the phase honest: every gram in the building can be traced to a document, a reason and a person.

---

## 1. Data model used

The tables existed since Phase 2; this phase gives them behaviour. One constraint changed:

| Table | Role in this phase |
| --- | --- |
| `warehouses` | three seeded stores: `MP` (raw materials), `PF` (finished goods), `QUAR` (`isQuarantine = 1`) |
| `materialBatches` | one row = one batch in one warehouse: `batchNumber`, `quantity`, `unit`, `productionDate`, `expiryDate`, `qualityStatus`, `purchasePrice`, `releasedAt`/`releasedBy` |
| `productBatches` | same for finished goods, with `unitCost` and `productionOrderId` (used from Phase 5) |
| `stockMovements` | append-only ledger: type, source and target warehouse, batch, quantity, unit cost, `referenceType`, reason, `userId`, `occurredAt` |
| `purchaseOrders` / `purchaseOrderItems` | `DRAFT → SENT → PARTIAL → RECEIVED | CANCELLED`, `receivedQty` per line |
| `goodsReceipts` / `goodsReceiptItems` | the reception document, with the batch number and expiry that were actually delivered |
| `suppliers` | master data plus `balance`, `paymentTerms`, purchase history |
| `stockCounts` / `stockCountItems` | present for the cycle-count document; the phase-4 count is recorded as an `ADJUSTMENT` movement with its reason |

**Schema change:** `uqMaterialBatchesNumber` and `uqProductBatchesNumber` are now
`(itemId, batchNumber, warehouseId)`. With `(itemId, batchNumber)` alone a transfer could only move
a whole batch to another store; with the warehouse in the key, a physical batch may be split
between `MP` and the workshop, which is what actually happens on a factory floor.
`docs/02-er-diagram.md` was regenerated and the schema checker still reports 68 tables / 0 problems.

## 2. API — 15 new routes (61 in total)

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/inventory/warehouses` | `INVENTORY_VIEW` | active stores, with the quarantine flag |
| GET | `/api/inventory/summary` | `INVENTORY_VIEW` | stock value, batch counts, quarantine and expiry counters, low-stock count |
| GET | `/api/inventory/stock` | `INVENTORY_VIEW` | paged batches: `search`, `warehouseId`, `kind`, `qualityStatus`, `expiringDays`, `availableOnly` |
| GET | `/api/inventory/movements` | `INVENTORY_VIEW` | paged ledger: `kind`, `batchId`, `warehouseId`, `movementType`, `from`, `to` |
| GET | `/api/inventory/fefo` | `INVENTORY_VIEW` | dry-run allocation: `kind`, `itemId`, `quantity` → batches, allocated, shortfall, sufficient |
| POST | `/api/inventory/adjust` | `INVENTORY_ADJUST` | physical count of one batch → `ADJUSTMENT` movement |
| POST | `/api/inventory/transfer` | `INVENTORY_TRANSFER` | move part of a batch to another warehouse → `TRANSFER` movement |
| GET | `/api/suppliers` | `SUPPLIERS_VIEW` | paged list, sortable, with order and material counts |
| GET | `/api/suppliers/{id}` | `SUPPLIERS_VIEW` | supplier + the materials it supplies + its last 10 orders |
| GET | `/api/purchaseOrders` | `PURCHASING_VIEW` | paged list: `search`, `status`, `supplierId`, `from`, `to` |
| GET | `/api/purchaseOrders/{id}` | `PURCHASING_VIEW` | order + lines (received/remaining) + receptions |
| POST | `/api/purchaseOrders` | `PURCHASING_CREATE` | create a `DRAFT`, numbered `BC-00001`, totals computed server-side |
| POST | `/api/purchaseOrders/{id}/send` | `PURCHASING_CREATE` | `DRAFT → SENT` |
| POST | `/api/purchaseOrders/{id}/cancel` | `PURCHASING_CREATE` | cancel while nothing has been received |
| POST | `/api/purchaseOrders/{id}/receipts` | `PURCHASING_APPROVE` | receive goods: batches + ledger + status in one transaction |

### Rules enforced in the repositories

1. **FEFO.** An allocation consumes released batches with stock, ordered
   `expiryDate IS NULL, expiryDate ASC, batchNumber ASC`. `GET /api/inventory/fefo` is a dry run:
   nothing is consumed, so a screen can show the plan before anyone confirms it.
2. **Quarantine is one-way.** A released batch never enters a quarantine warehouse and a batch held
   in quarantine never leaves for a selling warehouse (`409 QUARANTINE_RULE`). Goods received with
   `release = true` into the quarantine store answer `409 QUARANTINE_RECEPTION`.
3. **No silent stock.** A count that finds no difference is refused (`409 NO_ADJUSTMENT`) and both
   adjustments and transfers require a reason; the movement carries the acting user.
4. **Receptions cannot invent stock.** Only a `SENT` or `PARTIAL` order can be received
   (`409 PO_INVALID_STATE`), a line can never be over-received
   (`422 items.N.quantity = inventory.error.over_receipt`), and the batch number is mandatory.
5. **The reception is atomic.** Receipt header, receipt lines, batch creation or merge, the `IN`
   movement and the recomputed order status happen in one transaction.
6. **A received order is not cancelled.** Cancel is refused as soon as anything arrived
   (`409 PO_ALREADY_RECEIVED`).
7. **Audit.** Create / send / cancel / receive / adjust / transfer are written to `auditLogs` with
   the entity, its id and the payload keys.

### Error codes added by this phase

| Code | HTTP | When |
| --- | --- | --- |
| `QUARANTINE_RULE` | 409 | a transfer would break the quarantine walls |
| `QUARANTINE_RECEPTION` | 409 | released goods received into the quarantine warehouse |
| `NO_ADJUSTMENT` | 409 | the counted quantity equals the system quantity |
| `INSUFFICIENT_STOCK` | 409 | a transfer above what the batch holds |
| `PO_INVALID_STATE` | 409 | send/cancel/receive in a status that does not allow it |
| `PO_ALREADY_RECEIVED` | 409 | cancelling an order that is already partly in stock |
| `SUPPLIER_INACTIVE` | 409 | ordering from a supplier that was deactivated |

## 3. Desktop

`InventoryController`, `PurchasingController` and `SuppliersController` replace the Phase-1
placeholders; the three modules are delivered.

* **Inventory** — KPI strip (stock value, batches, quarantine, expiring soon) computed by the
  server; stock table by batch (code, item, batch, warehouse, quality badge, quantity, expiry,
  days left, unit cost, value) with warehouse/quality filters; a ledger tab with the movement type,
  both warehouses, the reason and the user; detail card with the selected batch and the two
  permission-aware actions (**Adjust**, **Transfer**).
* **Purchasing** — order list (number, supplier, status badge, dates, progress, amounts, receipts)
  with a status filter; detail panel with the lines (ordered / received / remaining / line total),
  the receptions and the actions allowed by the current status: **Send**, **Receive**, **Cancel**.
  The new-order dialog collects supplier, expected date and lines with a running total; the
  reception dialog collects the warehouse, an optional quality release, and per line the quantity,
  the batch number and the expiry — prefilled with what is still owed.
* **Suppliers** — list with contact, wilaya, payment terms and balance, an "active only" filter, and
  a detail panel with the coordinates, the balance, the last order and the materials supplied.

Buttons follow the same rule as the rest of the application: they are disabled when the permission
or the status does not allow the action, so the user understands the rule instead of hitting a
`409`. Demo mode ships `MockInventoryService`, `MockPurchasingService` and `MockSupplierService`
implementing the same rules on the demo dataset.

## 4. Tests

| Suite | Count | What this phase adds |
| --- | --- | --- |
| API integration (real MySQL 8) | 113 → 181 | warehouses and summary, supplier list/detail, order creation with server totals, draft cannot be received, over-reception, quarantine reception, partial reception → `PARTIAL`, batch in stock, FEFO refusal then allocation in expiry order, no-op adjustment, adjustment delta, quarantine transfer refusals, insufficient transfer, transfer to a selling store, ledger filters, cancel rules, 401 on the new endpoints |
| Desktop (JUnit 5 + TestFX) | 52 → 55 | `InventoryPurchasingUiTest`: three TestFX scenarios (inventory batches + KPIs + ledger tab, purchasing list and actions, supplier detail and language switch) |
| TestFX total | 5 → 8 | the three screens are driven through the real interface, headless via Xvfb |
| Schema / docs | — | `checkSchema.py` 68 tables / 0 problems, ER document regenerated, 708 i18n keys at parity |

## 5. Deliberately not in this phase

* **Purchase requests** (`purchaseRequests`) — the table exists and the API keeps `requestId`
  optional; the approval workflow belongs with the production planning of Phase 5.
* **Supplier invoices and payments** — `supplierInvoices` / `supplierPayments` drive
  `suppliers.balance`, which is Phase 7 (finance).
* **Batch release workflow** — quality releases a batch when reception declares it released; a
  dedicated release screen (with the quality-control record) is part of Phase 8 (safety/hardening).
* **Cycle-count documents** — the count is recorded as an `ADJUSTMENT` movement with its reason;
  the `stockCounts` header workflow lands with the inventory reports of Phase 8.

## 6. Definition of done — checked

- [x] Stock is computed from batches only; nothing writes a stock column.
- [x] FEFO serves the released batch that expires first, and refuses quarantined stock.
- [x] A released batch cannot enter quarantine and a quarantined batch cannot enter a selling store.
- [x] Every adjustment and transfer carries a reason and the acting user in the ledger.
- [x] A reception creates the batch, its expiry and its `IN` movement in one transaction.
- [x] An order never receives more than what remains on its lines.
- [x] An order with received stock cannot be cancelled.
- [x] FR / AR / EN strings added (708 keys, parity audited), RTL layout unchanged.
- [x] `mvn verify` and the API integration suite run in CI on the same commit.
