# ChemManager ERP — Phase 2 report: database + PHP API core

**Status: delivered and verified by GitHub Actions** (runs `42601289577` → `42602276505`).
Phase 2 built the server side the client already spoke to: the MySQL 8 database and the
framework-free PHP REST API on top of it.

| Part | Deliverable | Verification |
| --- | --- | --- |
| A — Database | `database/schema/*.sql` (68 tables), `database/seed/*.sql` (6 files) | static validator + **real MySQL 8** in CI: schema and seeds are executed on every push |
| B — API | `backend/` (front controller, router, PDO, auth, RBAC, 32 endpoints) | 25 unit tests + **77 end-to-end HTTP tests against a real MySQL instance** |
| Client | unchanged (still demo mode); `DATA_MODE=API` wirings lands with Phase 3 screens | 37 Java tests still green |

Latest green run: **desktop 37 tests, api 25 + 77 tests, BUILD SUCCESS**.

---

## Part A — Database

### A.1 What was asked

Write the database of the ERP in **camelCase, with no underscore character** in any identifier.

Applied to table names, column names, index names, constraint names, trigger names and views.
The only underscores left in the files belong to MySQL itself (`utf8mb4_unicode_ci`,
`time_zone`), because those are grammar, not our names.

### A.2 Deliverables

| Path | Content |
| --- | --- |
| `database/schema/01_core.sql` | `companies`, `departments`, `users`, `roles`, `permissions`, `rolePermissions`, `userRoles`, `apiTokens`, `auditLogs`, `notifications` |
| `database/schema/02_organisation.sql` | `cashAccounts`, `employees` |
| `database/schema/03_catalogue.sql` | `suppliers`, `customers`, `productCategories`, `rawMaterialCategories`, `products`, `rawMaterials`, `warehouses`, `materialBatches`, `productBatches`, `stockMovements`, `stockCounts`, `stockCountItems` |
| `database/schema/04_production.sql` | `formulations`, `formulationVersions`, `formulationItems`, `productionOrders`, `productionMaterials`, `productionOutputs`, `qualityChecks` + immutability triggers |
| `database/schema/05_commerce.sql` | purchasing (7), sales (10), distribution (3) + invoice-immutability triggers |
| `database/schema/06_finance.sql` | `cashTransactions`, `expenseCategories`, `expenses`, `accountingPeriods`, `attendance`, `leaveRequests`, `salaryPayments` |
| `database/schema/07_safety.sql` | `safetyPictograms`, `safetyDataSheets`, `materialPictograms`, `safetyInstructions`, `safetyIncidents` |
| `database/schema/08_system.sql` | `settings`, `documentSequences`, `backups`, views `vwStockBalances`, `vwLowStock` |
| `database/seed/01_permissions.sql` | 45 permissions — mirror of `com.chemmanager.security.Permission` |
| `database/seed/02_roles.sql` | 12 roles with their permission matrix — mirror of `DefaultRoles` |
| `database/seed/03_catalogue.sql` | departments, product/material categories, warehouses, cash accounts, expense types |
| `database/seed/04_safety.sql` | GHS01…GHS09 pictograms in FR/AR/EN |
| `database/seed/05_adminUser.sql` | first administrator, bcrypt hash, `mustChangePassword = 1` |
| `database/seed/06_settings.sql` | company identity, 14 settings, document sequences, 12 accounting periods |
| `database/README.md` | conventions, installation, least-privilege account, enforced rules |
| `docs/02-er-diagram.md` | **generated** ER model of all 68 tables |
| `tools/db/checkSchema.py` | static validator (CI) |
| `tools/db/generateErDoc.py` | ER documentation generator (CI checks it is up to date) |

**68 tables**, 2 views, 4 triggers, 6 seeders.

### A.3 Verification (automatic, in CI)

```
python3 tools/db/checkSchema.py            → 68 tables, 0 problems
python3 tools/db/generateErDoc.py --check  → docs/02-er-diagram.md is up to date
mysql < database/schema/*.sql, database/seed/*.sql   → tables=70 (68 tables + 2 views),
                                             permissions=45, roles=12, users=1   [GitHub Actions, MySQL 8.0]
```

`checkSchema.py` is not a smoke test: it parses every DDL and seed file and fails the build when

* an identifier contains an underscore (the rule the user set);
* a `FOREIGN KEY` points to a table or column that does not exist;
* a table is referenced before it is created (wrong migration order);
* a table has no primary key, engine or charset;
* a seed inserts into an unknown table, or grants a permission that does not exist;
* the seeded permissions/roles drift from the Java `Permission` and `DefaultRoles` enums.

Local cross-check with a real SQL parser (`sqlglot`, MySQL dialect): **117 statements, 0 syntax
errors** (trigger bodies excluded — `sqlglot` has no MySQL trigger grammar). CI cannot install
`sqlglot`, so it is not part of the pipeline.

### A.4 Design decisions worth knowing

| Decision | Reason |
| --- | --- |
| Stock is **never** a column | `materialBatches`/`productBatches` hold the quantity per batch; `stockMovements` is an append-only ledger; `vwStockBalances` aggregates it |
| FEFO | `idxMaterialBatchesFefo(expiryDate, qualityStatus)` + a `qualityStatus` of `QUARANTINE`/`RELEASED`/`REJECTED`; sales only see `RELEASED` |
| Formulations are never generated | `formulationItems` stores what staff typed; an approved `formulationVersions` row is locked by `trgFormulationVersionsLocked` and any change creates a new version |
| A batch is traceable both ways | `productionMaterials.materialBatchId` → consumed, `productionOutputs.productBatchId` → produced, `invoiceItems.productBatchId` → sold |
| An issued invoice cannot be edited | `trgInvoicesFinalized`; corrections are `creditNotes` |
| Gapless Algerian numbering | `documentSequences(documentType, periodKey)` locked with `SELECT … FOR UPDATE` inside the transaction (`FA`, `BL`, `DE`, `CF`, `OF`…) |
| No posting in a closed period | `accountingPeriods` (seeded with 2026-01…2026-10 closed, 11–12 open) |
| Money in DZD, VAT 19 % | `DECIMAL(15,2)` amounts, `DECIMAL(7,4)` rates, `currency CHAR(3)` per document |
| Audit trail append-only | `auditLogs`; the application account has only `SELECT, INSERT, UPDATE` — no `DELETE`, no DDL |
| Trilingual reference data | `nameFr` / `nameAr` / `nameEn` on categories, pictograms, roles, permissions — the UI never needs a join to translate |

### A.5 Server requirement (important)

MySQL must run with **`lowerCaseTableNames=0`** (the Linux default). On Windows/macOS installers it
defaults to `1` and would fold the camelCase table names to lowercase. It cannot be changed once
tables exist, so it has to be set before creating the database — documented in
`database/README.md`.

### A.6 Client alignment

`JsonMapper` now uses `LOWER_CAMEL_CASE` instead of `SNAKE_CASE`: a field is named identically in
MySQL, PHP and Java (`amountHt`, `productBatchId`), so the API needs no translation layer and the
JSON of a document is readable by anyone who knows the schema.

---

## Part B — PHP REST API

### Structure

```
backend/public/index.php      front controller (only exposed file)
backend/src/Core/             Config, Database (PDO), Request, Response, Router, Validator,
                              Audit, Logger, ApiException
backend/src/Security/         Auth (bcrypt + opaque bearer tokens), Authorizer (RBAC)
backend/src/Http/App.php      composition root: CORS, authentication, permission check, dispatch
backend/src/Repositories/     all SQL, prepared statements only
backend/src/routes.php        route table + the permission each endpoint requires
backend/tools/console.php     db:check, user:password, token:purge, role:list, install
```

### What it does

| Concern | Implementation |
| --- | --- |
| Envelope | one shape for everything: `{success,data,meta}` / `{success:false,error:{code,message,details}}` |
| Naming | `lowerCamelCase` identical in MySQL, PHP and Java (`amountHt`, `productBatchId`) |
| Authentication | bcrypt (cost 12, transparent re-hash), 32-byte opaque token returned once, **only its sha256** stored in `apiTokens.tokenHash`, expiry 8 h, revocation on logout / password change / admin reset |
| Forced password change | the seeded temporary password only allows `/auth/me`, `/auth/password`, `/auth/logout`; everything else answers `403 PASSWORD_CHANGE_REQUIRED` |
| Brute force | per-account counter, lockout for N minutes, `Retry-After`, every attempt in `auditLogs` (WARNING/CRITICAL) |
| Authorisation | permissions recomputed from `rolePermissions` on **every** request; the route table is the only place a permission is attached to an endpoint (`SUPER_ADMIN` is the single wildcard) |
| SQL safety | prepared statements only, no identifier interpolation, sort keys whitelisted per repository, one placeholder per occurrence (MySQL native prepares refuse reuse) |
| Errors | no stack trace, no SQL, no account enumeration; details only in debug mode |
| Audit | every mutation and every session event written to `auditLogs` with user, action, module, entity, IP, severity |
| Dashboard | every figure aggregated from the ledger (`invoices`, `expenses`, `materialBatches`, `productBatches`, `productionOrders`, `vwLowStock`) — nothing hardcoded |

### Endpoints (32)

Health `GET /api/health` · auth `POST /api/auth/login`, `GET /api/auth/me`,
`POST /api/auth/logout`, `POST /api/auth/password` · dashboard `GET /api/dashboard/summary` ·
products `GET/POST /api/products`, `GET/PUT/DELETE /api/products/{id}`,
`GET /api/products/categories` · materials `GET /api/rawMaterials`,
`GET /api/rawMaterials/{id}`, `GET /api/rawMaterials/{id}/batches`,
`PUT /api/rawMaterials/{id}`, `GET /api/rawMaterials/categories` · users
`GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}`, `POST /api/users/{id}/password` ·
roles `GET /api/roles`, `GET /api/permissions`, `GET /api/departments`,
`GET/PUT /api/roles/{id}/permissions` · settings `GET/PUT /api/settings`,
`PUT /api/settings/company` · audit `GET /api/auditLogs`.

### What the tests prove

`backend/tests/unit/run.php` (25): router matching/405/404, envelope shape, JSON keeps camelCase and
Arabic text, validator collecting every error, authorizer refusal message, request helpers, config
defaults.

`backend/tests/integration/run.php` (77, real MySQL 8 + real HTTP): health reports the 68 tables ·
wrong password refused without revealing the account · seeded admin signs in with 45 permissions ·
temporary password blocks other endpoints · weak password refused · password change revokes sessions ·
old token dies · product validation returns every field error · Arabic name round-trips · duplicate
SKU → 422 `DUPLICATE` · search finds the row · pagination meta complete · partial update keeps the
other fields · soft delete hides the product · material categories seeded (7) · dashboard sections
present · invalid period refused · DZD company identity · setting updated and read back · audit trail
contains LOGIN and CREATE · 12 roles / 45 permissions · VIEWER can read products but cannot create
them, cannot list users, gets 403/404 as designed · admin password reset revokes the user's sessions ·
soft delete of a user · 5 wrong passwords lock the account with `Retry-After` · unknown account
refused.

### Bugs the real database caught (and that a mock would have hidden)

1. MySQL refuses a `CHECK` on a column carrying an explicit foreign-key referential action
   → `fkStockMovementsMaterialBatch/ProductBatch` dropped to the default `RESTRICT`.
2. `Illegal mix of collations (utf8mb4_0900_ai_ci … utf8mb4_unicode_ci)` when joining
   `vwStockBalances.itemKind`: every DDL/seed file and the PDO connection now pin
   `utf8mb4_unicode_ci`, and the view literals are cast accordingly.
3. PDO with `EMULATE_PREPARES=false` rejects a reused named placeholder: the `LIKE` searches now
   use one placeholder per occurrence.
4. Patch-style `PUT` sent only the changed fields, so the validator rejected the absent ones: a rule
   now applies only to a field that is actually present.

## Next — Phase 3 (formulations)

1. Screen: formulation list → version history → items → approval, on the API endpoints of this
   phase extended with `formulations`, `formulationVersions`, `formulationItems`.
2. The application still never generates a formula: it stores and versions what qualified staff
   enter, and locks an approved version (`trgFormulationVersionsLocked`).
3. Switch `ApiClient` to the real endpoints (`DATA_MODE=API`) while keeping the demo dataset.
