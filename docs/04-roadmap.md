# ChemManager ERP — Delivery Roadmap

Eight phases. A phase is only closed when it compiles, its tests pass **and** the previous
phase still works. **Phase 2 does not start before the Phase 1 review is approved.**

---

## Phase 1 — Foundation, shell and dashboard ✅ *(delivered)*

| Deliverable | Detail |
| --- | --- |
| Maven project | Java 21 + JavaFX 21.0.4, Jackson, FontAwesomeFX, POI, JUnit 5, `mvn javafx:run` |
| Documentation | architecture, ER model, design system, roadmap, RBAC matrix, operations |
| Design system | `app.css` + light/dark palettes, typography, spacing, component library |
| Security foundation | 45 permissions, 12 roles, PBKDF2 hashing, session object, permission-filtered navigation |
| Localisation | FR / AR (RTL) / EN bundles (509 keys), runtime switch, parity test |
| Login | validation, password toggle, throttling, remembered username, demo accounts |
| Shell | grouped sidebar (18 modules), header (title, search, notifications, theme, language, profile), status bar |
| Dashboard | 5+ KPI cards from stored transactions, 3 charts, 3 rankings, 2 alert lists, activity feed, 9 date-range presets, Excel export |
| Products / raw materials | paged searchable tables, filters, detail dialogs, export |
| Users & permissions | user list, roles and permission matrix |
| Settings | general, company, data source (switch demo ↔ API at runtime) |
| Tests | bundle parity, PBKDF2, role matrix, money, date ranges, dashboard aggregation |
| Demo data | 2 years of sales, purchases, expenses, production orders, batches, users, activities |

**Acceptance criteria met:** `mvn clean verify` passes on CI; the application starts with
`mvn javafx:run`; every navigation entry opens a working screen; the language and theme
switches work without restart; no KPI is hardcoded; no button is inert.

## Phase 2 — Database and PHP API core ✅ *(delivered)*

* ✅ MySQL 8 schema in **camelCase without underscores**: 68 tables in `database/schema/*.sql`,
  seeders for permissions, roles, admin user, categories, GHS pictograms, company settings and
  numbering (`database/seed/*.sql`). ER model generated from the DDL into
  `docs/02-er-diagram.md`, executed on a real MySQL 8 in CI. See `docs/PHASE-2-REPORT.md`.
* ✅ PHP 8 REST API without framework: front controller, router, PDO connection factory with
  prepared statements, JSON envelope, error handler, CORS for the desktop client.
* Authentication: `POST /auth/login` (bcrypt), bearer tokens (`apiTokens`, hashed),
  `POST /auth/logout`, `GET /auth/me`, token expiry and revocation.
* Middleware: authentication, permission check per route (`ROUTE_PERMISSIONS`), rate limiting,
  request logging, audit writer.
* Endpoints: users, roles, permissions, products, raw materials, suppliers, customers,
  categories, settings, health.
* Client: `data.mode=API` becomes the default in production; the existing `api/*` services are
  validated against the real API.
* **Acceptance:** an authenticated `GET /api/products` returns the rows with pagination; a
  token without `PRODUCTS_VIEW` gets `403`; SQL injection and password-hash checks pass;
  the desktop switches from demo to API without code changes.

## Phase 3 — Formulations (no generation by the app) ✅ *(delivered)*

* ✅ `formulations`, `formulationVersions`, `formulationItems` screens: version history, cost
  from current material prices, comparison of two versions, approval workflow.
  See `docs/PHASE-3-REPORT.md`.
* ✅ Rules: only `FORMULATIONS_APPROVE` holders can approve; approved versions are locked; a
  change creates a new version; no automatic/recommended formula is ever produced.
* ⏭ PDF export of a version (JasperReports template) with Arabic and French layouts —
  moved to the document work of Phase 6, which owns the PDF engine (Quotation → Invoice →
  Delivery note), so the formulation sheet reuses it instead of duplicating a second renderer.
* **Acceptance:** an approved version cannot be edited (API rejects it with `409`), the cost
  recomputes when a material price changes, and every change is in the audit log.

## Phase 4 — Inventory, warehouses, purchasing ✅ *(delivered)*

* ✅ Warehouses, material/product batches, quarantined vs released stock and the append-only
  `stockMovements` ledger: stock is summed from the batches and never stored as a column.
* ✅ FEFO allocation on issue, expiry alerts, quarantine walls, and a refusal (`409
  NO_ADJUSTMENT`) when a count finds no difference; batch numbers are unique per warehouse so a
  batch can be split between stores by a transfer.
* ✅ Purchasing: orders (`DRAFT → SENT → PARTIAL → RECEIVED | CANCELLED`), atomic partial goods
  receipts that create the batch, its expiry and its `IN` movement, supplier master data screens.
* ✅ Physical counts and transfers that write a movement with a reason and the acting user instead
  of rewriting history.
* ⏳ Purchase requests, supplier invoices/payments and the quality-release screen belong to
  Phases 5/7/8 — see `docs/PHASE-4-REPORT.md`.
* **Acceptance:** a reception creates a batch visible in the stock list; issuing consumes the
  oldest released batch first; balances equal the sum of the batches and of the movements.

## Phase 5 — Production ✅ *(delivered)*

* ✅ Manufacturing orders from an approved formulation version, material reservation and actual
  consumption per batch, waste capture, quality checks, batch release, finished-goods entry.
  See `docs/PHASE-5-REPORT.md`.
* ✅ Batch genealogy view: finished batch → consumed material batches → supplier receptions.
* ✅ Real production cost per batch (materials + packaging + other) and variance vs. standard.
* ✅ A shortfall leaves the order in `WAITING_MATERIALS` with the missing quantities instead of an
  order that cannot run; consumption the operator over-reports is kept and shown as a variance.
* ⏭ Labour and energy costing — `otherCost` is entered and audited; a cost model built on payroll
  belongs with Phase 7.
* **Acceptance:** a finished batch cannot be sold before `qualityStatus = RELEASED`; the
  genealogy of a batch is complete; stock and cost are consistent after a partial production.

## Phase 6 — Sales, customers, distribution, documents ✅ *(delivered)*

* ✅ Quotations → orders → delivery notes (FEFO picks) → invoices → payments → credit notes.
* ✅ Customer master data with credit limits and balances, price segments, statements.
* ✅ Delivery routes, vehicles, drivers, proof of delivery — the receiver's name is recorded on the
  note at the stop and printed on it; a drawn signature is carried to Phase 8 (`signaturePath`).
* ✅ Printable documents (invoice, delivery note, quotation, statement) in AR and FR with the
  company identity from Settings; finalized invoices become immutable.
* **Acceptance (met):** an issued invoice cannot be modified (`INVOICE_FINALIZED`, only credit notes);
  printing produces the document in FR, EN and AR; a delivery note decrements the exact batches it
  lists, in FEFO order and in the same transaction.

## Phase 7 — Finance, expenses, employees, payroll

* Cash accounts, receipts/payments from sales and purchasing, cash book, period closing.
* Expenses by category with attachments and vehicle/line allocation.
* Employees, attendance, leave, advances, monthly salary payments booked as expenses.
* Financial statements: margin by product, cash flow, period result.
* **Acceptance:** no posting is possible in a closed period; the cash book reconciles with
  payments and expenses; a closed period cannot be reopened without super-admin action.

## Phase 8 — Reports, chemical safety, hardening

* Report centre with JasperReports templates (sales, purchases, production, stock, expiry,
  profitability, customer statements, safety registers) and Excel exports.
* Chemical safety: SDS library with expiry tracking, pictograms, storage conditions, PPE,
  incident register and missing-document alerts.
* Hardening: full RBAC audit, backup/restore drill, performance pass on the largest tables,
  user guide (AR/FR), installer and first-run configuration.

---

## Cross-phase quality gates

| Gate | Requirement |
| --- | --- |
| Build | `mvn clean verify` green on GitHub Actions for every push |
| Tests | every new rule (locking, FEFO, credit limit, closed period) has a unit test |
| Data | migrations are forward-only and re-runnable; seeders idempotent |
| Security | no secret in the repository, prepared statements only, permissions checked server-side |
| i18n | no text outside the bundles; parity test green |
| UX | no dead button, no placeholder value, empty states and error states translated |

## Phase 1 review checklist (for approval)

1. `mvn javafx:run` → login screen → sign in with `admin` / `admin123`.
2. Dashboard: change the range → every KPI, chart and ranking changes; export to Excel.
3. Sidebar: open each implemented module; the 13 planned modules show their phase.
4. Switch the language to العربية → the whole window mirrors, texts are translated.
5. Toggle the dark theme; reload a module (the theme is remembered across restarts).
6. Open the notification popup, the user menu, Settings → data source probe.
7. `mvn test` → all suites green.
