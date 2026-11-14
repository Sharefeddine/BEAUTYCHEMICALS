# Phase 8 — Chemical safety, the report centre, hardening

> Scope: the chemical register the HSE inspector asks for — safety data sheets, handling
> instructions, pictograms and the incident book — and the report centre that puts every operational
> report of the workshop in one screen with an Excel export, on both halves (PHP API + JavaFX
> desktop), verified in CI against a real MySQL 8 database.

**Guiding rule:** a hazardous substance is only as safe as its paperwork is recent. A sheet older
than three years is reported **obsolete** even when nothing replaced it, a substance without a sheet
is **red** in the register, and an incident is investigated before it is closed — never reopened.
The screen shows the state; the server enforces it.

---

## 1. Data model used

The five safety tables existed since Phase 2; this phase gives them behaviour and seeds them.
**No schema change**: 68 tables, schema checker still reports 0 problems.

| Table | Role in this phase |
| --- | --- |
| `safetyPictograms` | the nine GHS pictograms, `code` (`GHS01`…`GHS09`), `nameFr` / `nameEn` / `nameAr`, `sortOrder` |
| `safetyDataSheets` | one row per document: `documentType` (`SDS \| TECHNICAL \| DISPOSAL \| TRANSPORT`), `filePath`, `issueDate`, `revision`, `source`, `isCurrent`, `uploadedBy` |
| `materialPictograms` | the join between a raw material and its pictograms (a set, replaced atomically) |
| `safetyInstructions` | one row per material (`uqSafetyInstructionsMaterial`): `ppe`, `storage`, `handling`, `firstAid`, `fireMeasures`, `spillage` |
| `safetyIncidents` | `INC-00001`, `incidentDate`, `incidentType` (`SPILL \| EXPOSURE \| FIRE \| DAMAGE \| OTHER`), `severity` (`MINOR \| MODERATE \| MAJOR \| CRITICAL`), `description`, `actionsTaken`, `status` (`OPEN → INVESTIGATING → CLOSED`), `closedAt`, `reportedBy` |
| `rawMaterials` (read/write) | `hazardClass`, `hazardPictograms`, `sdsAvailable`, `sdsFileName` — mirrored whenever a sheet or a pictogram set changes |
| `auditLogs` | every safety write is logged under module `SAFETY`, incidents with severity `WARNING` |

### Seed (`database/seed/09_safety.sql`)

Ten statements, loaded after `08_finance.sql`:

* a **current** SDS for `MP-0001` (`REV-4`, 2025-02-14) and `MP-0002` (`REV-2`, 2024-09-02);
* a current but **obsolete** SDS for `MP-0003` (`REV-1`, 2021-03-11 — older than the 3-year window);
* handling instructions for `MP-0001` and `MP-0002`;
* the pictogram links aligned with each material's real `hazardPictograms` string (`MP-0001` GHS05,
  `MP-0002` GHS07, `MP-0003` GHS05), so the "aligned" invariant is not a fake;
* two incidents: `INC-00001` (`SPILL`, `MINOR`, `CLOSED`) and `INC-00002` (`EXPOSURE`, `MODERATE`,
  `OPEN`);
* `MP-0004`…`MP-0012` stay **sheet-less on purpose**: the "missing sheet" filter needs data.

## 2. API — 17 new routes (173 in total)

| Area | Routes | Permission |
| --- | --- | --- |
| Register | `GET /api/safety/overview`, `GET /api/safety/pictograms`, `GET /api/safety/materials` (search, `risk`, paging), `GET /api/safety/materials/{id}` | `SAFETY_VIEW` |
| Sheet library | `GET /api/safety/sheets` (search, `documentType`, `currentOnly`, paging), `POST /api/safety/sheets` | `SAFETY_VIEW` / `SAFETY_EDIT` |
| Handling | `POST /api/safety/materials/{id}/instructions`, `POST /api/safety/materials/{id}/pictograms` | `SAFETY_EDIT` |
| Incidents | `GET /api/safety/incidents` (status, severity, search, paging + `meta.summary`), `POST /api/safety/incidents`, `POST /api/safety/incidents/{id}/status` | `SAFETY_VIEW` / `SAFETY_EDIT` |
| Report centre | `GET /api/reports/catalogue`, `GET /api/reports/sales`, `GET /api/reports/purchases`, `GET /api/reports/stock`, `GET /api/reports/production`, `GET /api/reports/safety` | `REPORTS_VIEW` |

### Rules enforced on the server

1. **Validity window** — `SafetyRepository::SHEET_VALIDITY_YEARS = 3`. A current SDS whose
   `issueDate` is null or older than three years is returned with `isObsolete = true`; the register
   lists it under `risk=obsolete`. The window is inlined into the SQL (`INTERVAL 3 YEAR`) because a
   bound parameter cannot sit inside `INTERVAL`.
2. **One current document per type** — registering a sheet demotes the previous current sheet of the
   same `documentType` inside one transaction, and mirrors `sdsAvailable` / `sdsFileName` on the
   material for `SDS`. Old revisions are kept.
3. **Pictograms are a set** — `POST …/pictograms` replaces the join rows and rewrites
   `rawMaterials.hazardPictograms` as the comma-joined codes; an unknown code is `422`.
4. **Incident lifecycle** — `OPEN → INVESTIGATING → CLOSED`; `CLOSED` is terminal (reopening answers
   `409 INVALID_STATUS`) and `OPEN → CLOSED` is refused with the same code. Numbers are
   `INC-` + five digits, continuation-safe (the next number is probed, not assumed).
5. **Report windows** — every windowed report parses its dates with `checkdate` (the parser is
   lenient: `2026-13-01` would roll over) and refuses `to < from` with `422`.

### The report centre

| Key | Group | Periodic | Content |
| --- | --- | --- | --- |
| `result` | FINANCE | yes | the result of a month (Phase 7) |
| `margin` | FINANCE | yes | margin by product (Phase 7) |
| `cashflow` | FINANCE | yes | inflow / outflow by month and source (Phase 7) |
| `sales` | COMMERCE | yes | totals, by month, by customer (with outstanding), by product |
| `purchases` | COMMERCE | yes | totals (incl. receipts), by status, by month, by supplier |
| `stock` | OPERATIONS | no | value per warehouse, batches to watch, expired, quarantine, below minimum |
| `production` | OPERATIONS | yes | planned / produced / waste, yield, unit cost, by product and status |
| `safety` | SAFETY | no | documented share of the register, obsolete sheets, missing sheets, incidents by severity and type |

Each report reconciles with its own detail lines — the integration suite asserts it (`sales` totals =
sum of its months, `stock` total = sum of its warehouses, `production` yield = produced / planned).

## 3. Desktop client

### The chemical safety screen (`safety.fxml` + `SafetyController`)

* five indicators: substances, hazardous, documented (%), obsolete sheets, open incidents;
* the **register** table: code, name, hazard class, sheet state badge (*à jour* / *obsolète* /
  *manquante*), revision, issue date, storage — activated by double-click;
* the **file panel**: identity and pictograms, the sheet list with its current / archived state and
  the *Register a sheet* dialog, the handling instructions with their editor and pictogram picker, and
  the incident history of the substance;
* the **incident book** tab: number, date, substance, type, severity badge, status badge, description,
  status and severity filters, the counters line, *Report an incident*, *Start investigation* and
  *Close incident* — the last two follow the selection and the lifecycle;
* *Export register* writes the current page to Excel through `ExcelExporter` (Apache POI).

Write gestures are hidden behind `SAFETY_EDIT`: a read-only role (the warehouse manager, for
instance) sees the register and the incidents but gets no button.

### The report centre (`reports.fxml` + `ReportCentrePane`)

Eight catalogue cards above the three statements of Phase 7. Opening a card renders the report — its
indicators, then one table per breakdown — and *Export to Excel* writes the main table of the report
on screen. The window pickers of the screen drive both the statements and the centre.

### Service layer

`SafetyService` and `ReportCentreService`, each with an API implementation
(`ApiSafetyService`, `ApiReportCentreService`) and an offline demo (`MockSafetyService`,
`MockReportCentreService`) that replays the same rules and sums its own totals, so the demo
reconciles exactly like the server. `ApiEndpoints` gained 17 constants; `ApiContractTest` now checks
all of them against `routes.php`.

## 4. Tests

| Suite | What it covers |
| --- | --- |
| `SafetyServiceMockTest` (19) | the 3-year window, the missing and obsolete filters, paging and search, the material file, sheet registration and demotion, instructions upsert, pictogram set replacement, incident numbering, the lifecycle and its refusals |
| `ReportCentreServiceMockTest` (7) | the catalogue of eight, totals = sum of lines for sales / purchases / stock / production, the yield and unit cost, the safety join, the window |
| `SafetyUiTest` (6) | KPIs, register rows, the risk filter, opening a substance file, the incident book and its selection, a read-only role, the Arabic layout |
| `ReportCentreUiTest` (3) | the eight cards, switching to the warehouse valuation, the statements still below |
| Integration suite (470 checks) | 26 new checks: pictograms, overview, the three risk filters, the material file, sheet registration and demotion, the missing-file refusal, the sheet library, instructions, the pictogram set and its refusal, the incident register and summary, reporting, the three lifecycle refusals, the read-only role, the eight reports, the sales and stock reconciliations, the production yield, the safety report, the impossible window |

Desktop total: **134 tests** (35 TestFX-driven, up from 34 screens/tests to 39 scenes). Bundle
parity: 3 bundles × **1455 keys**.

### A flaky test, fixed at the root

`WaitForAsyncUtils.waitFor` polls its condition on a background thread; a bare `robot.lookup` from
there walks the JavaFX children while the shell rebuilds them and occasionally threw
`IndexOutOfBoundsException` inside `Parent.lookupAll` — that is what failed the Phase-7 build once
(`LoginScreenUiTest.sidebarNavigationOpensTheClickedModule`). The new
`com.chemmanager.support.UiWaits` runs every probe **on the FX thread** (`Platform.runLater` +
`FutureTask`) and retries a raced lookup, and the eleven UI tests now use it. The same pass caught
two conditions that had been inverted during the conversion (`#startButton`, `#detailHost`).

## 5. Verification

CI run on this branch (`PHP API (lint + unit + integration on MySQL 8)` and `JavaFX client (build +
tests)`):

* PHP: lint of every file, unit suite, **470 integration checks** against MySQL 8 with seeds
  `01`…`09`; 173 routes; 68 tables; 0 schema problems.
* Desktop: build of the client, **134 tests**, 0 failures; translation parity across FR / EN / AR
  (1455 keys each); RTL preserved.

## 6. Deliverables

| Path | Content |
| --- | --- |
| `database/seed/09_safety.sql` | the safety seed |
| `backend/src/Repositories/SafetyRepository.php` | the rules and the queries of the register |
| `backend/src/Controllers/SafetyController.php` | the 10 safety actions |
| `backend/src/Repositories/ReportRepository.php` | the five new reports + the catalogue |
| `backend/src/Controllers/ReportController.php` | the six new report actions |
| `backend/src/routes.php` | 17 new routes (173) |
| `src/main/resources/fxml/safety.fxml`, `fxml/reports.fxml` | the two screens |
| `src/main/java/com/chemmanager/controller/safety/SafetyController.java` | the register screen |
| `src/main/java/com/chemmanager/controller/reports/ReportCentrePane.java` | the catalogue and the reports |
| `src/main/java/com/chemmanager/model/safety/*`, `model/analytics/*` | 9 + 7 records |
| `src/main/java/com/chemmanager/service/{SafetyService,ReportCentreService}.java` + `api/*` + `mock/*` | the two services, twice |
| `src/test/java/com/chemmanager/support/UiWaits.java` | the FX-thread-safe scene probes |
| `src/test/java/com/chemmanager/{service,ui}/*` | the four new suites |

## 7. What is deliberately not in this phase

* No document upload: the sheet library stores the path in the DMS, not the file. Uploading a PDF to
  the server is a hardening item.
* No automatic reminder when a sheet approaches its third birthday: the register shows it, the
  notification engine only reports stock and production alerts today.
* The annual HSE report (`reports/safety` is a snapshot, not a signed document) — the printable
  document templates of Phase 6 would need an HSE layout.
