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

## Phase 2 — Database and PHP API core *(database delivered, API in progress)*

* ✅ MySQL 8 schema in **camelCase without underscores**: 68 tables in `database/schema/*.sql`,
  seeders for permissions, roles, admin user, categories, GHS pictograms, company settings and
  numbering (`database/seed/*.sql`). ER model generated from the DDL into
  `docs/02-er-diagram.md`. See `docs/PHASE-2-DATABASE.md`.
* ⏳ PHP 8 REST API without framework: front controller, router, PDO connection factory with
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

## Phase 3 — Formulations (no generation by the app)

* `formulations`, `formulationVersions`, `formulationItems` screens: version history, cost
  from current material prices, comparison of two versions, approval workflow.
* Rules: only `FORMULATIONS_APPROVE` holders can approve; approved versions are locked; a
  change creates a new version; no automatic/recommended formula is ever produced.
* PDF export of a version (JasperReports template) with Arabic and French layouts.
* **Acceptance:** an approved version cannot be edited (API rejects it with `409`), the cost
  recomputes when a material price changes, and every change is in the audit log.

## Phase 4 — Inventory, warehouses, purchasing

* Warehouses, material/product batches, quarantined vs released stock, stock movements ledger.
* FEFO allocation on issue, expiry alerts, negative-stock refusal unless `INVENTORY_ADJUST`.
* Purchasing: requests → orders → partial goods receipts (creating batches) → supplier
  invoices → payments; supplier master data screens.
* Cycle counts and adjustments that create movements instead of rewriting history.
* **Acceptance:** issuing a product consumes the oldest expiring batch first; a receipt
  creates a batch visible in raw materials; balances equal the sum of movements.

## Phase 5 — Production

* Manufacturing orders from an approved formulation version, material reservation and actual
  consumption per batch, waste capture, quality checks, batch release, finished-goods entry.
* Batch genealogy view: finished batch → consumed material batches → supplier receptions.
* Real production cost per batch (materials + packaging + other) and variance vs. standard.
* **Acceptance:** a finished batch cannot be sold before `qualityStatus = RELEASED`; the
  genealogy of a batch is complete; stock and cost are consistent after a partial production.

## Phase 6 — Sales, customers, distribution, documents

* Quotations → orders → delivery notes (FEFO picks) → invoices → payments → credit notes.
* Customer master data with credit limits and balances, price segments, statements.
* Delivery routes, vehicles, drivers, proof of delivery (signature capture).
* Printable documents (invoice, delivery note, quotation, statement) in AR and FR with the
  company identity from Settings; finalized invoices become immutable.
* **Acceptance:** an issued invoice cannot be modified; printing produces both languages;
  a delivery note decrements the exact batches it lists.

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
