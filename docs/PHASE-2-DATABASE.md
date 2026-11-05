# ChemManager ERP — Phase 2 (part 1): database

**Status:** schema, seeds and validation delivered — verified by GitHub Actions.
**Next part of Phase 2:** the PHP REST API (`backend/`) on top of this schema.

---

## 1. What was asked

Write the database of the ERP in **camelCase, with no underscore character** in any identifier.

Applied to table names, column names, index names, constraint names, trigger names and views.
The only underscores left in the files belong to MySQL itself (`utf8mb4_unicode_ci`,
`time_zone`), because those are grammar, not our names.

## 2. Deliverables

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

## 3. Verification (automatic, in CI)

```
python3 tools/db/checkSchema.py           → 68 tables, 0 problems
python3 tools/db/generateErDoc.py --check → docs/02-er-diagram.md is up to date
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

## 4. Design decisions worth knowing

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

## 5. Server requirement (important)

MySQL must run with **`lowerCaseTableNames=0`** (the Linux default). On Windows/macOS installers it
defaults to `1` and would fold the camelCase table names to lowercase. It cannot be changed once
tables exist, so it has to be set before creating the database — documented in
`database/README.md`.

## 6. Client alignment

`JsonMapper` now uses `LOWER_CAMEL_CASE` instead of `SNAKE_CASE`: a field is named identically in
MySQL, PHP and Java (`amountHt`, `productBatchId`), so the API needs no translation layer and the
JSON of a document is readable by anyone who knows the schema.

## 7. Next (still Phase 2)

1. `backend/` — front controller, router, PDO layer, JSON envelope, JWT-less bearer tokens
   (`apiTokens.tokenHash`), RBAC middleware reading `rolePermissions`, audit writer.
2. Endpoints of the Phase-1 modules: `auth`, `users`, `roles`, `products`, `rawMaterials`,
   `settings`, `dashboard`, `health`.
3. `backend/tools/console.php` — installer, password reset, backup runner, expired-token purge.
4. Point the JavaFX client at the API (`ApiClient` + `DATA_MODE=API`) and keep the demo mode.
