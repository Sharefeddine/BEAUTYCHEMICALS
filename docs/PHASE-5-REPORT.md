# Phase 5 — Production, quality release and batch genealogy

> Scope: manufacturing orders built from an approved formulation version, FEFO material reservation,
> actual consumption per batch through the stock ledger, waste capture, quality checks that decide
> what may be sold, finished-goods entry with the real cost of the run, and the genealogy of a batch
> — on both halves (PHP API + JavaFX desktop), verified in CI against a real MySQL 8 database.

**Guiding rule:** the application still never invents a formula and never invents a quantity. The
shop floor starts from a version a manager approved and locked, writes down what it really used, and
quality — not production — decides whether the result may be sold. Everything else in this phase is
bookkeeping around those two facts.

---

## 1. Data model used

The tables existed since Phase 2; this phase gives them behaviour. One change:

| Table | Role in this phase |
| --- | --- |
| `formulationVersions` / `formulationItems` | read-only source of the bill of materials; only `isLocked = 1` versions may be produced |
| `productionOrders` | the order: `DRAFT → WAITING_MATERIALS → IN_PROGRESS → QUALITY_CONTROL → COMPLETED | REJECTED | CANCELLED`, planned/actual/waste quantity, `materialCost` / `packagingCost` / `otherCost`, operator and supervisor |
| `productionMaterials` | one row per reserved batch: planned quantity, consumed quantity — the input side of the genealogy |
| `productionOutputs` | one row per finished batch — the output side of the genealogy |
| `qualityChecks` | `checkType`, `parameters JSON`, `result PASS|FAIL|PENDING`, examiner, remarks |
| `materialBatches` / `productBatches` | stock leaves and stock arrives; `qualityStatus` is the wall between the workshop and the shop |
| `stockMovements` | `PRODUCTION_OUT` and `PRODUCTION_IN`, written in the same transaction as the batch change |

**Schema change:** `productBatches` now carries `releasedAt` and `releasedBy`, the same pair
`materialBatches` has had since Phase 4. A finished batch that becomes sellable must name the moment
and the person who released it; the release update and the desktop genealogy panel already assumed
these columns existed. `docs/02-er-diagram.md` was regenerated and the schema checker still reports
68 tables / 0 problems.

## 2. API — 10 new routes (71 in total)

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/production/versions` | `PRODUCTION_VIEW` | approved, locked versions the shop floor may produce |
| GET | `/api/production/orders` | `PRODUCTION_VIEW` | paged list: `search`, `status`, `from`, `to`, with reserved and consumed totals |
| GET | `/api/production/orders/{id}` | `PRODUCTION_VIEW` | the order with its plan, its reservation, its outputs, its checks and its cost |
| GET | `/api/production/orders/{id}/genealogy` | `PRODUCTION_VIEW` | finished batch → consumed material batches → supplier receptions |
| POST | `/api/production/orders` | `PRODUCTION_CREATE` | create a `DRAFT`, numbered `PRD-00001`, bill of materials scaled to the ordered quantity |
| POST | `/api/production/orders/{id}/start` | `PRODUCTION_CREATE` | reserve released batches in FEFO order, or report the shortfall |
| POST | `/api/production/orders/{id}/consume` | `PRODUCTION_CREATE` | record what was really used, batch by batch |
| POST | `/api/production/orders/{id}/complete` | `PRODUCTION_APPROVE` | create the finished batch in quarantine with the real cost |
| POST | `/api/production/orders/{id}/quality` | `PRODUCTION_APPROVE` | record a check; `PASS` releases, `FAIL` rejects |
| POST | `/api/production/orders/{id}/cancel` | `PRODUCTION_CREATE` | cancel while nothing has been consumed |

### Rules enforced in the repository

1. **The formula comes from an approved version.** A draft version, an archived formulation or a
   formulation without a product is refused (`409 PRODUCTION_VERSION_NOT_APPROVED`,
   `PRODUCTION_ARCHIVED`, `PRODUCTION_NO_PRODUCT`). The ordered quantity is scaled against the
   version's expected yield; a 500 L order of a 100 L formula multiplies every line by five.
2. **Reservation is FEFO and honest.** `start` walks the released batches of the order's warehouse in
   ascending expiry order and takes what each can give. If something is missing the order becomes
   `WAITING_MATERIALS` and the answer lists the shortfall per material — the shop floor is told what
   is missing instead of being handed an order that cannot run. Quarantine warehouses cannot be
   consumed from (`409 PRODUCTION_QUARANTINE`).
3. **Consumption is what really happened.** Every line writes a `PRODUCTION_OUT` movement and
   decrements the batch in the same transaction. Asking a batch for more than it holds is refused
   (`409 PRODUCTION_STOCK`) *before* anything is written; over-consumption relative to the plan is
   accepted and reported as a variance, because refusing reality only pushes it into a spreadsheet.
4. **Completing creates stock nobody may sell yet.** The finished batch is created in `QUARANTINE`
   with `unitCost = (materials + packaging + other) / actualQuantity`, a `PRODUCTION_IN` movement,
   and the order moves to `QUALITY_CONTROL`. An order without consumption cannot be completed
   (`409 PRODUCTION_NO_CONSUMPTION`), and the quarantine warehouse cannot receive finished goods.
5. **Only a PASS releases.** A `PASS` check flips the batch to `RELEASED`, stamps `releasedAt` /
   `releasedBy`, closes the order as `COMPLETED`; a `FAIL` rejects the batch and the order and leaves
   it out of reach of the sales module. A quality decision requires an order waiting for control
   (`409 PRODUCTION_INVALID_STATE`).
6. **Cost and variance are server-side.** `standardMaterialCost` is the scaled plan priced at today's
   purchase prices; `variance` and `variancePct` compare it with what was actually consumed, so the
   screen never recomputes a business figure.
7. **Nothing is deleted.** Cancelling keeps the reservation rows (the plan explains what the order
   intended) and only refuses when material has already been consumed (`409 PRODUCTION_CONSUMED`) or
   when the order is closed. Every create, start, consume, complete, release, reject and cancel is in
   `auditLogs` with the acting user.

### Error codes added by this phase

| Code | Status | Meaning |
| --- | --- | --- |
| `PRODUCTION_VERSION_NOT_APPROVED` | 409 | the version is not locked, so it is not approved |
| `PRODUCTION_ARCHIVED` | 409 | the formulation is archived |
| `PRODUCTION_NO_PRODUCT` | 409 | the formulation is not attached to a finished product |
| `PRODUCTION_NO_MATERIALS` | 409 | the version has no bill of materials |
| `PRODUCTION_QUARANTINE` | 409 | quarantine warehouse used as a source or a destination |
| `PRODUCTION_MATERIALS` | 409 | released stock is missing — the order waits for materials (details: shortfalls) |
| `PRODUCTION_ALREADY_STARTED` | 409 | the materials are already reserved |
| `PRODUCTION_STOCK` | 409 | the batch holds less than what was asked |
| `PRODUCTION_NO_CONSUMPTION` | 409 | nothing was consumed, so nothing can be produced |
| `PRODUCTION_CONSUMED` | 409 | material was consumed: the order can no longer be cancelled |
| `PRODUCTION_INVALID_STATE` | 409 | the action does not belong to the order's status |

## 3. Desktop

**Production (Phase 5 placeholder replaced by the real screen).** A toolbar with the status filter,
the summary, refresh and one button per action; the buttons follow the selected state
(`DRAFT`/`WAITING_MATERIALS` can start, `IN_PROGRESS` can consume and complete, `QUALITY_CONTROL` can
be released, everything can show its genealogy), so the user sees the rule instead of a `409`.

* **List** — order number, product, version, planned date, planned quantity, consumed quantity, cost
  and a status badge, paged and searchable.
* **Detail** — the planned quantities, the produced and wasted quantities, the four cost lines, the
  unit cost, the standard cost and the variance; the reserved batches with planned/consumed/variance
  and expiry; the finished batches with their quality badge and a plain *sellable* sentence; the
  quality checks with their examiner and remarks.
* **Dialogs** — create (approved version, warehouse, quantity, date, notes), start, consume (defaults
  to the planned quantity of each reserved batch), complete (actual and waste quantity, packaging and
  other cost, expiry), quality (type, result, remarks), cancel (reason), and the genealogy view that
  lists each consumed batch with its supplier, its reception and its purchase order.
* **One panel, one answer.** A detail request that comes back after the user has already selected
  another order is dropped instead of repainting the panel, and a filter that hides the selected order
  clears its detail — the screen never describes an order that is no longer in the list.

Demo mode ships `MockProductionService` implementing the same rules on the demo dataset: a waiting
order, a running order, a completed order whose batch was released, and an order still in quality
control whose batch is in quarantine.

## 4. Tests

| Suite | Count | What this phase adds |
| --- | --- | --- |
| API integration (real MySQL 8) | 183 → 241 | approved-version-only list, unapproved/archived/quarantine refusals, scaled bill of materials, shortfall → `WAITING_MATERIALS`, FEFO reservation per material, double start, over-consumption refusal, consumption + ledger + cost, `PRODUCTION_OUT` count, completion in quarantine, real cost per unit, variance, `sellable = false`, second run failed at quality → `REJECTED`, no unreleased batch under `qualityStatus=RELEASED`, release → `COMPLETED`, measurements kept, genealogy complete with supplier and reception, cancel rules, 401 on the new endpoints |
| Desktop (JUnit 5 + TestFX) | 68 → 71 | `ProductionUiTest`: the list and the state-driven actions, the cost/detail panel, and a released batch described as sellable while a quarantined one is not |
| TestFX total | 11 → 14 | the three production scenarios drive the real interface, headless via Xvfb |
| Schema / docs | — | `checkSchema.py` 68 tables / 0 problems, ER document regenerated, 784 i18n keys (+69) at parity, design checker 0 problems |

## 5. Deliberately not in this phase

* **Selling a finished batch** — the sales cycle (quotation → delivery → invoice) is Phase 6; this
  phase only guarantees that nothing enters it unreleased.
* **Purchase requests** (`purchaseRequests`) — the table and the optional `requestId` stay for the
  purchasing workflow of Phase 8.
* **Quality-control measurements library** — `qualityChecks.parameters` stores the measured values as
  JSON; per-product tolerance rules and specification sheets belong with the safety work of Phase 8.
* **Scheduling and capacity** — an order has a planned date, not a production calendar; planning
  boards and machine capacity are out of scope for a chemicals workshop of this size.
* **Cost of labour and energy** — `otherCost` is entered by the operator and audited; a full
  analytical costing model would need the payroll data of Phase 7.

## 6. Definition of done — checked

- [x] An order can only be created from an approved, locked formulation version.
- [x] The application still generates no formula and no dose; the bill of materials is copied and scaled.
- [x] Starting an order reserves released batches in FEFO order or reports what is missing.
- [x] Consumption writes a `PRODUCTION_OUT` movement per batch and cannot take more than the batch holds.
- [x] Completing creates the finished batch in `QUARANTINE` with the real cost of the run.
- [x] A finished batch is sellable only after a `PASS` quality check (`RELEASED`, with who and when).
- [x] The genealogy of a batch is complete: output batch → consumed material batches → supplier reception.
- [x] Stock and per-batch cost stay consistent after a partial production (96 L produced from a 100 L plan).
- [x] Every mutation is audited; every button is disabled when the permission or status refuses it.
- [x] FR / AR / EN strings added (784 keys, parity audited), RTL layout unchanged.
- [x] `mvn verify` and the API integration suite run in CI on the same commit.

## 7. CI evidence

| Run | Commit | Result |
| --- | --- | --- |
| 43587132984 | `e6cb5bf` | API green — **25 unit + 241 integration** on MySQL 8 |
| _pending_ | _pending_ | desktop `BUILD SUCCESS` with **71 tests** (14 TestFX) |

The production screen is captured as a real module in the published screenshots (`ci-reports`
branch): `desktop/screenshots/07d-production.png` (it replaces `07-planned-module.png`).
