# ChemManager ERP — Phase 1 delivery report

**Date:** 5 November 2026 · **Branch:** `arena/9682ec1e-beautychemicals` · **Build:** green on GitHub Actions

Phase 1 delivers the foundation of the product: architecture, data model, design system, the
document set, and a **runnable** JavaFX application with login, application shell, dashboard,
product/raw-material registers, users & permissions and settings — trilingual (AR/FR/EN),
light/dark, no hardcoded figures and no inert controls.

---

## 1. Verification performed

Everything below was executed on CI (JDK 21, headless Xvfb) because the delivery environment has
no local JDK:

| Check | Command | Result |
| --- | --- | --- |
| Compilation | `mvn -B verify` inside `xvfb-run` | 121 sources, 0 errors |
| Unit tests | same run | **37 tests, 0 failures, 0 errors, 0 skipped** |
| End-to-end boot | `ApplicationSmokeTest` | login scene → shell → all 18 modules opened → locale switch AR/EN/FR → theme switch → sign-out |
| FXML integrity | `python3 tools/fxml_check.py` | 0 problems (ids, controllers, handlers, `%keys`, style classes) |
| Translation parity | `python3 tools/i18n/audit.py` + `BundleParityTest` | 511 keys in FR/AR/EN, 0 missing |
| Visual proof | 10 screenshots captured by the smoke test | `docs/screenshots/` |

The smoke test is part of the build, so any future change that breaks a screen, a controller
binding or a stylesheet fails CI instead of reaching the customer.

## 2. What the application does today

**Login** — validated credentials, password reveal, throttling after 5 failures, remembered
username, translated errors, demo account hint, live theme and language switch on the form itself.

**Shell** — collapsible grouped sidebar (8 groups / 18 modules, permission-filtered with phase
badges), header with page title/subtitle, global module search (Ctrl+K style filtering), notification
popup with unread badge, theme toggle, language menu, profile menu, status bar (data source,
connection state, clock).

**Dashboard** — 13 KPI tiles (sales today/month/period, purchases, expenses, gross margin with %
chip, net profit, inventory value, receivables, cash position, production quantity, expiring
batches, average basket), 3 charts (sales vs purchases, revenue vs costs, production), category
pie, best sellers, most profitable, top customers, low-stock and expiry (FEFO) alert lists, audit
activity feed, 9 date-range presets + custom dates, Excel export of the current period.
Every number is an aggregation of stored transactions — the service computes them with the same
logic the SQL backend will use, and a test asserts the KPI equals the sum recomputed from the data.

**Products** — 23 demo products, search (SKU/name/barcode), category and type filters, active-only
filter, stock badges (out/low/normal), prices, margin, stock value, detail dialog, paging, export.

**Raw materials** — 28 materials with chemical name, category, supplier, stock, hazard badge,
SDS availability, expiry badge, low-stock and hazardous filters, detail dialog with batch,
storage conditions and dates, export.

**Users & permissions** — 6 accounts with roles/status/last sign-in, and the 12 role cards with
their permission chips (the same matrix the API will enforce), export.

**Settings** — general (theme, language, currency, VAT, invoice prefix), company identity used on
documents, data-source panel (probe API, switch demo ↔ API at runtime), backup policy.

**Planned modules** — 13 screens (formulations, production, inventory, purchasing, suppliers,
sales, customers, distribution, finance, expenses, employees, reports, safety) open a real page
listing their planned capabilities and delivery phase, with working navigation.

## 3. Deliverables

| Area | Path |
| --- | --- |
| Build | `pom.xml` (Java 21, JavaFX 21.0.4, Jackson, FontAwesomeFX, POI, JUnit 5) |
| Application | `src/main/java/com/chemmanager/**` (121 classes) |
| Screens | `src/main/resources/fxml/*.fxml` (20 files) |
| Design system | `src/main/resources/css/{app,theme-light,theme-dark}.css` |
| Translations | `src/main/resources/i18n/messages_{fr,ar,en}.properties` (511 keys) + sources in `tools/i18n/` |
| Tests | `src/test/java/com/chemmanager/**` (7 suites, 37 tests) |
| Documentation | `README.md`, `docs/01-architecture.md`, `docs/02-er-diagram.md`, `docs/03-design-system.md`, `docs/04-roadmap.md`, `docs/05-security-rbac.md`, `docs/07-operations.md` |
| Evidence | `docs/screenshots/` (login, dashboard light/dark, Arabic, products, materials, users, settings, planned module, English) |
| CI | `.github/workflows/ci.yml` — build + tests + screenshots, reports published to `ci-reports` |

## 4. How to run it

```bash
# JDK 21 + Maven 3.9 required
mvn clean verify      # compile + 37 tests
mvn javafx:run        # start the client (demo dataset, no server needed)
```

Sign in with `admin` / `admin123` (also `manager/manager123`, `production/prod123`, `store/store123`,
`sales/sales123`, `accounting/acc123`).

Production mode: `-Ddata.mode=API -Dapi.base.url=https://erp.company.dz/api`.

## 5. Review checklist

1. Login screen: switch theme and language (the whole window mirrors for Arabic).
2. Sign in as `admin` → dashboard loads with the 12-month default range.
3. Change the period → KPIs, charts, rankings and alerts all recompute; export to Excel.
4. Sidebar: open Products, Raw materials, Users, Settings; try the 13 planned modules.
5. Sign out, sign in as `accounting` → the finance modules appear, production/stock settings do not.
6. `mvn test` → 37 green, including the headless boot test.

## 6. Known limits / deliberately deferred

* The PHP API and the MySQL schema are **Phase 2**: the client already speaks to them
  (`service/api/*`, `data.mode=API`) but no server is shipped yet, so the delivered application
  runs on the deterministic demo dataset.
* CRUD (create/edit/delete) for products, materials, users and settings is Phase 2/7 work; the
  Phase 1 screens are read-only registers with working filters, dialogs and exports, as scoped.
* PDF/JasperReports documents, Excel report centre and the chemical-safety library arrive with
  their modules (phases 4–8).
* Formulation authoring is intentionally absent: the application never generates or suggests a
  formula; it will only store and approve what qualified staff enter (Phase 3).

## 7. Remaining roadmap (summary)

| Phase | Scope | Main deliverables |
| --- | --- | --- |
| 2 | Database + PHP API core | MySQL schema/seeders, PDO repositories, auth tokens, RBAC middleware, CRUD endpoints, client switch to API |
| 3 | Formulations | Versioned, approved (immutable) formulas, cost calculation, comparison, PDF |
| 4 | Inventory + purchasing | Warehouses, batches, FEFO ledger, quarantines, supplier orders/receipts/invoices |
| 5 | Production | Manufacturing orders, consumption per batch, quality release, batch genealogy, real cost |
| 6 | Sales, customers, distribution | Quotes → orders → deliveries → invoices → payments, AR/FR documents, routes |
| 7 | Finance, expenses, payroll | Cash/bank, period closing, expenses, employees, salaries |
| 8 | Reports, safety, hardening | Report centre, SDS library, incidents, backup drill, user guide, installer |

**Phase 2 starts only after this report is approved.**
