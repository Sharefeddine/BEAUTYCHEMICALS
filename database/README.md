# ChemManager ERP — database

MySQL 8 schema, seeders and conventions for the ChemManager ERP backend.
The client (JavaFX) never connects to MySQL: only the PHP API does, with a restricted account.

```
database/
  schema/    DDL, executed in file-name order
    01_core.sql          companies, departments, users, roles, permissions, tokens, audit, notifications
    02_organisation.sql  cash accounts, employees
    03_catalogue.sql     partners, categories, products, raw materials, warehouses, batches, stock ledger
    04_production.sql    formulations, production orders, quality, locks and triggers
    05_commerce.sql      purchasing, sales, distribution, immutability triggers
    06_finance.sql       cash book, expenses, accounting periods, attendance, payroll
    07_safety.sql        GHS pictograms, SDS library, safety instructions, incidents
    08_system.sql        settings, document numbering, backups, reporting views
  seed/      reference and bootstrap data, executed in file-name order
    01_permissions.sql   45 permissions (mirror of com.chemmanager.security.Permission)
    02_roles.sql         12 roles and their permission matrix (mirror of DefaultRoles)
    03_catalogue.sql     categories, departments, warehouses, cash accounts, expense types
    04_safety.sql        GHS01…GHS09 pictograms
    05_adminUser.sql     first administrator (bcrypt hash, must change password)
    06_settings.sql      company identity, settings, document sequences, accounting periods
```

## Naming conventions (strict)

| Rule | Example |
| --- | --- |
| **lowerCamelCase everywhere — no underscore** in table, column, index, constraint, trigger or view names | `purchaseOrderItems`, `amountHt`, `idxProductsSku`, `fkPaymentsInvoice` |
| One plural noun per table | `users`, `stockMovements`, `salaryPayments` |
| Primary key always `id` (`BIGINT UNSIGNED AUTO_INCREMENT`) | `id` |
| Foreign keys `<entity>Id` | `customerId`, `productBatchId` |
| Audit columns | `createdAt`, `updatedAt`, `createdBy`, `updatedBy` |
| Soft delete | `deletedAt DATETIME NULL` |
| Booleans | `isActive`, `isLocked`, `isSystem`, `isQuarantine` |
| Amounts / quantities | `DECIMAL(15,2)` money, `DECIMAL(15,3)` quantity, `DECIMAL(7,4)` rates |
| Enum values | SCREAMING_SNAKE (`DRAFT`, `WAITING_MATERIALS`) — they are *values*, not identifiers |
| Indexes / constraints | `idx<Table><Purpose>`, `uq<Table><Purpose>`, `fk<Table><Target>`, `ck<Table><Rule>` |
| Views | `vw<Name>` |

The only underscores left in the files belong to MySQL itself (`utf8mb4_unicode_ci`, `time_zone`,
`ON DUPLICATE KEY UPDATE`): they are reserved words, not our identifiers.

> **Server requirement:** MySQL must run with `lowerCaseTableNames=0` (the Linux default).
> On Windows/macOS installers this setting defaults to 1 and would fold the camelCase table
> names to lowercase; set it in `my.ini`/`my.cnf` **before** creating the database, because it
> cannot be changed after tables exist.

## Installation

```bash
mysql -u root -p -e "
  CREATE DATABASE chemmanager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# schema, in order
for file in database/schema/*.sql; do
  mysql -u root -p chemmanager < "$file" || { echo "failed: $file"; exit 1; }
done

# reference data, in order
for file in database/seed/*.sql; do
  mysql -u root -p chemmanager < "$file" || { echo "failed: $file"; exit 1; }
done
```

All seeds are idempotent (`ON DUPLICATE KEY UPDATE` / `NOT EXISTS`), so re-running them is safe.

The schema files contain `DELIMITER $$` blocks for the triggers: they must be executed with the
`mysql` client (as above), not through a library that splits on every semicolon.

## Application account (least privilege)

```sql
CREATE USER 'chemapp'@'10.0.0.%' IDENTIFIED BY '<strong-password>';
GRANT SELECT, INSERT, UPDATE ON chemmanager.* TO 'chemapp'@'10.0.0.%';
-- deliberately NOT granted: DELETE, DROP, ALTER, GRANT
-- audit, stock movements and financial documents are therefore append-only at the DB level
```

Deletions are logical (`deletedAt`, status `CANCELLED`): the application never issues `DELETE`.

## First sign-in

`seed/05_adminUser.sql` creates `admin` with the temporary password **`admin123`** (bcrypt cost 12)
and `mustChangePassword = 1`. Change it immediately during installation:

```bash
php backend/tools/console.php user:password admin '<strong-password>'
```

The API refuses any other request until the password has been changed.

## Enforced business rules (database level)

| Rule | Where |
| --- | --- |
| An approved formulation version is immutable | `trgFormulationVersionsLocked` (04_production.sql) |
| Stock can never go negative | `trgMaterialBatchesNoNegative`, `trgProductBatchesNoNegative` + `CHECK` constraints |
| An issued invoice cannot be modified | `trgInvoicesFinalized`, `trgInvoiceItemsImmutable` (05_commerce.sql) |
| Paid amount, quantities and totals are non-negative | `CHECK` constraints |
| A batch line carries exactly one batch type | `ckStockMovementsTarget` |
| Numbering is gapless per document type and period | `documentSequences` + `SELECT … FOR UPDATE` in the API |
| No posting into a closed period | `accountingPeriods.status = CLOSED` checked by the API before insert |
| Audit trail is append-only | `auditLogs` + no `UPDATE/DELETE` grant |

## Reporting views

| View | Purpose |
| --- | --- |
| `vwStockBalances` | released stock per item, with minimum level and nearest expiry |
| `vwLowStock` | items at or below their minimum level (drives the dashboard alert card) |

## Verifying the files without a MySQL server

```bash
python3 tools/db/checkSchema.py
```

The checker parses every DDL/seed file and verifies: balanced statements, **no underscore in any
identifier**, every foreign key targets an existing table/column, creation order respects
dependencies, every table has a primary key/engine/charset, and the seeded permissions and roles
are identical to the Java `Permission` and `DefaultRoles` enumerations. It runs in CI, so a
schema change cannot drift from the client model.
