# ChemManager ERP — PHP REST API

Framework-free PHP 8.2 API in front of the MySQL schema of `database/`.
The JavaFX client never touches MySQL: it sends JSON over HTTP and receives one envelope.

```
backend/
  public/index.php        front controller (the only web-exposed file)
  src/
    autoload.php          PSR-4 autoloader, no Composer
    Core/                 Config, Database (PDO), Request, Response, Router, Validator, Audit, Logger,
                          ApiException
    Security/             Auth (bcrypt + opaque bearer tokens), Authorizer (RBAC)
    Http/App.php          composition root: CORS, authentication, permission check, dispatch, errors
    Repositories/         all SQL lives here, prepared statements only
    Controllers/          thin HTTP layer
    routes.php            route table + permission required by each endpoint
  tools/console.php       db:check, user:password, token:purge, role:list, install
  tests/unit/run.php      no database needed (`php backend/tests/unit/run.php`)
  tests/integration/run.php  end-to-end against a seeded MySQL (`php backend/tests/integration/run.php`)
```

## Install

```bash
cp backend/config/config.example.php backend/config/config.php   # then edit, keep it out of Git
chmod 640 backend/config/config.php
php backend/tools/console.php db:check
php backend/tools/console.php user:password admin '<new-password>'
```

Apache points its document root at `backend/public` (`.htaccess` included). For development:

```bash
php -S 127.0.0.1:8080 -t backend/public
```

## Envelope

```json
{"success": true, "data": {...}, "meta": {"page": 1, "pageSize": 25, "total": 42, "totalPages": 2}}
{"success": false, "error": {"code": "VALIDATION_FAILED", "message": "...", "details": {"sku": "validation.required"}}}
```

Field names are `lowerCamelCase` and identical to the MySQL columns — `amountHt`, `productBatchId`,
`qualityStatus` — so the same name is used in SQL, PHP and Java. Validation messages are i18n keys
(`validation.required`), resolved by the client in the current language.

## Endpoints

| Method | Path | Permission |
| --- | --- | --- |
| GET | `/api/health` | public |
| POST | `/api/auth/login` | public |
| GET | `/api/auth/me` | authenticated |
| POST | `/api/auth/logout` | authenticated |
| POST | `/api/auth/password` | authenticated |
| GET | `/api/dashboard/summary?from&to` | `DASHBOARD_VIEW` |
| GET | `/api/products` (`search`, `categoryId`, `type`, `status`, `sort`, `direction`, `page`, `pageSize`) | `PRODUCTS_VIEW` |
| GET | `/api/products/categories` | `PRODUCTS_VIEW` |
| GET | `/api/products/{id}` | `PRODUCTS_VIEW` |
| POST | `/api/products` | `PRODUCTS_CREATE` |
| PUT | `/api/products/{id}` | `PRODUCTS_EDIT` |
| DELETE | `/api/products/{id}` (soft delete) | `PRODUCTS_DELETE` |
| GET | `/api/rawMaterials`, `/api/rawMaterials/{id}`, `/api/rawMaterials/{id}/batches` | `MATERIALS_VIEW` / `INVENTORY_VIEW` |
| PUT | `/api/rawMaterials/{id}` | `MATERIALS_EDIT` |
| GET | `/api/users`, `/api/users/{id}` | `USERS_VIEW` |
| POST/PUT/DELETE | `/api/users`, `/api/users/{id}`, `/api/users/{id}/password` | `USERS_MANAGE` |
| GET | `/api/roles`, `/api/permissions`, `/api/departments`, `/api/roles/{id}/permissions` | `USERS_VIEW` |
| PUT | `/api/roles/{id}/permissions` | `USERS_MANAGE` |
| GET | `/api/settings` | `SETTINGS_VIEW` |
| PUT | `/api/settings`, `/api/settings/company` | `SETTINGS_EDIT` |
| GET | `/api/auditLogs` | `AUDIT_VIEW` |

## Security

* Passwords: `password_hash()` bcrypt, cost 12, transparent re-hash on sign-in, strength rule
  (8+ characters, letters and digits), temporary-password forced change.
* Sessions: 32 random bytes returned once; only the sha256 is stored (`apiTokens.tokenHash`), with
  expiry, revocation on logout / password change / reset, `ipAddress` and `userAgent` kept for audit.
* Brute force: per-account counter (`users.failedAttempts`), lockout for N minutes, `Retry-After`
  header, every failure written to `auditLogs`.
* Authorisation: recomputed from `rolePermissions` on every request; the route table is the only
  place where a permission is attached to an endpoint.
* SQL: prepared statements only, column/table names never interpolated from input (sort keys are
  whitelisted per repository).
* Errors: no stack trace, no SQL, no account enumeration; `display_errors=0`; details only in debug.

## Verification

* `php backend/tests/unit/run.php` — router, envelope, validator, authorizer, request helpers.
* `php backend/tests/integration/run.php` — health, sign-in, forced password change, RBAC (viewer
  refused on writes), product CRUD, dashboard aggregates, settings, audit trail, account lockout,
  token revocation.
* Both run in GitHub Actions against a **real MySQL 8 service container** loaded with
  `database/schema/*.sql` and `database/seed/*.sql`.
