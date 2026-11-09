# Phase 3 — Formulations

> Scope: formula families, versioning, bill of materials, cost from current prices, approval
> lock, version comparison — on both halves (PHP API + JavaFX desktop), verified in CI against a
> real MySQL 8 database.

**Guiding rule, restated because it shapes every screen:** the application *never* generates a
formula. No suggested dosage, no auto-completed composition, no "recommended" percentages. It
stores exactly what a qualified person typed, prices it, freezes it on approval and keeps every
version forever.

---

## 1. Data model used

The tables were created in Phase 2 and are used as designed:

| Table | Role in this phase |
| --- | --- |
| `formulations` | the family: `code`, `nameFr`, `nameAr`, `productId`, `status` (`DRAFT`/`PENDING`/`APPROVED`/`ARCHIVED`), `currentVersionId`, `approvedBy`, `approvedAt`, `notes` |
| `formulationVersions` | one immutable-after-approval content: `version`, `instructions`, `expectedYield`, `yieldUnit`, `isLocked`, `lockedAt`, `approvedBy`, `approvedAt` |
| `formulationItems` | the bill of materials of one version: `materialId`, `quantity`, `unit`, `percentage`, `phase`, `tolerancePct`, `sortOrder` |

Nothing was added to the schema: the phase is a behaviour phase, not a migration phase.

## 2. API — 11 new routes (46 in total)

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/api/formulations` | `FORMULATIONS_VIEW` | paged list, filters `search`, `status`, `productId` |
| POST | `/api/formulations` | `FORMULATIONS_EDIT` | create a family (code auto-generated `FML-0001` when omitted) |
| GET | `/api/formulations/{id}` | `FORMULATIONS_VIEW` | family + version history + current version with items |
| PUT | `/api/formulations/{id}` | `FORMULATIONS_EDIT` | rename, re-link the product, edit notes |
| POST | `/api/formulations/{id}/archive` | `FORMULATIONS_EDIT` | archive without touching the versions |
| GET | `/api/formulations/{id}/versions` | `FORMULATIONS_VIEW` | version history, newest first, with item count and cost |
| POST | `/api/formulations/{id}/versions` | `FORMULATIONS_EDIT` | next version, copying the current content |
| GET | `/api/formulations/{id}/versions/{versionId}` | `FORMULATIONS_VIEW` | one version with its items and cost breakdown |
| PUT | `/api/formulations/{id}/versions/{versionId}` | `FORMULATIONS_EDIT` | replace instructions / yield / items — **409 once approved** |
| POST | `/api/formulations/{id}/versions/{versionId}/approve` | `FORMULATIONS_APPROVE` | lock, promote, stamp the approver |
| GET | `/api/formulations/{id}/compare?left=&right=` | `FORMULATIONS_VIEW` | material-by-material diff + cost delta |

Server-side rules, all enforced inside `FormulationRepository` (so they hold for any caller, not
only for the current controller):

1. **No generation.** Items arrive from the client; the server validates them (existing material,
   quantity > 0) and stores them as entered.
2. **Approval requires content.** An empty version answers `422` — an empty bill of materials
   cannot be produced.
3. **Approval is irreversible.** `isLocked`, `lockedAt`, `approvedBy`, `approvedAt`, the family
   `status`, `currentVersionId` and the product link are written in one transaction; any later
   `PUT` answers `409 FORMULATION_LOCKED`.
4. **Cost follows the prices.** Cost is `SUM(quantity × rawMaterials.purchasePrice)` computed on
   read, never cached: change a purchase price and every formulation re-prices itself.
5. **Percentages.** Computed automatically when every line of the version shares the same unit
   (they add up to 100), otherwise left at 0 rather than inventing a cross-unit percentage.
6. **Audit.** Every mutation (create/update/version/approve/archive) is written to `auditLogs`
   with the user, the entity id and the payload keys.

## 3. Desktop

`FormulationsController` replaces the Phase-1 placeholder; the module is now flagged delivered and
appears with its real screen (`FORMULATIONS` in `NavigationModule`).

* **List** — code, name (FR/AR depending on the interface language), finished product, status
  badge, current version, version count, material cost, approver; search + status filter + paging
  through the shared `DataTable` (Ctrl+F, Esc, empty states).
* **Detail panel** — versions selector (`v1 🔒`), facts (status, versions, cost, yield, material
  count, approver), the bill of materials as a table (code, name, quantity, %, phase, line cost,
  on-hand stock, hazard class) and the operating procedure text.
* **New formula** — French/Arabic name, finished product, notes.
* **New version** — copies the current content, then opens for editing; the approved version is
  untouched.
* **Edit composition** — add/remove raw materials, edit quantities and phases inline, set the
  expected yield and the operating procedure. Quantities ≤ 0 and duplicate lines are refused.
* **Approve** — confirmation showing the version and its material cost, then a locked, stamped
  version.
* **Compare** — v(n-1) → v(n): added / removed / changed / unchanged lines with the cost delta.

Buttons are permission-aware (`FORMULATIONS_EDIT` for create/version/edit, `FORMULATIONS_APPROVE`
for approval) and disabled when the selected version is locked, so the UI explains the rule
instead of letting the user hit a `409`.

Demo mode has its own `MockFormulationService` implementing the same rules, so the workflow can be
walked through without a server — and `FormulationRulesTest` asserts those rules on it.

## 4. Tests

| Suite | Count | What this phase adds |
| --- | --- | --- |
| API integration (real MySQL 8) | 86 → 113 | create/validate a family, empty version refused, items stored and priced, percentages, approval lock, double approval, version copy, edit-after-approval `409`, comparison, archive, 404 |
| Desktop unit (JUnit 5) | 39 → 47 | `FormulationRulesTest`: no generation, lock, empty approval, costing, percentages, comparison delta, history survival |
| Contract | included in the 47 | `ApiContractTest` also covers `/api/formulations` |

## 5. Deliberately not in this phase

* **PDF export of a formulation sheet** — the PDF engine (Quotation/Invoice/Delivery note with FR
  and AR layouts) belongs to Phase 6; the formulation sheet will reuse it rather than ship a
  second, divergent renderer.
* **Production consumption** — reading an approved version into a manufacturing order is Phase 5
  (`productionOrders.formulationVersionId` is already in place).

## 6. Definition of done — checked

- [x] `mvn clean verify` green on CI, API integration suite green on MySQL 8.
- [x] An approved version cannot be edited: `409 FORMULATION_LOCKED`, and the UI disables the button.
- [x] The cost recomputes from the current purchase prices on every read.
- [x] Every mutation is in the audit log.
- [x] The application produces no formula, dosage or mixing instruction of its own.
- [x] FR / AR / EN strings added (574 keys, parity audited), RTL layout unchanged.
