# ChemManager ERP — Security and RBAC

Security model: **the client is not trusted**. Every permission is verified by the PHP API on
each request; the JavaFX interface only hides what the user cannot use, it never grants access.

---

## 1. Authentication

| Aspect | Implementation |
| --- | --- |
| Login | `POST /api/auth/login` with `{username, password}`; the client sends no other credential |
| Password storage | `password_hash($pwd, PASSWORD_BCRYPT)` in MySQL; demo mode uses PBKDF2-HMAC-SHA256, 120 000 iterations, 16-byte random salt, constant-time comparison |
| Session | opaque 32-byte random token, stored **hashed** (`sha256`) in `api_tokens`, sent as `Authorization: Bearer <token>` |
| Lifetime | 8 h sliding, absolute maximum 12 h; `POST /api/auth/logout` revokes immediately |
| Throttling | 5 failed attempts per username/IP → lockout 2 minutes, exponential after that; every failure is audited |
| Inactive account | login refused with `login.error.inactive` |
| Client storage | token kept in memory only (no file, no preference); the remembered *username* is the only persisted value |
| Transport | HTTPS in production; the desktop never opens a MySQL connection |

### Authorisation header flow

```
LoginController → AuthService.login(username, password)
   → ApiClient.post("/auth/login")            → PHP: verify bcrypt, issue token
   ← LoginResponse{token, user, permissions}
SessionContext.set(Session(token, user, permissions, expiresAt))
   → NavigationManager filters the sidebar with session.permissions()
   → every later request: ApiClient adds Authorization: Bearer <token>
   → PHP middleware recomputes the permissions from the database (never from the token payload)
```

## 2. Permission catalogue (44 codes)

| Module | Codes |
| --- | --- |
| Dashboard | `DASHBOARD_VIEW` |
| Products | `PRODUCTS_VIEW`, `PRODUCTS_CREATE`, `PRODUCTS_EDIT`, `PRODUCTS_DELETE`, `PRODUCTS_PRICE_EDIT` |
| Raw materials | `MATERIALS_VIEW`, `MATERIALS_EDIT` |
| Formulations | `FORMULATIONS_VIEW`, `FORMULATIONS_EDIT`, `FORMULATIONS_APPROVE` |
| Production | `PRODUCTION_VIEW`, `PRODUCTION_CREATE`, `PRODUCTION_APPROVE` |
| Inventory | `INVENTORY_VIEW`, `INVENTORY_ADJUST`, `INVENTORY_TRANSFER` |
| Purchasing | `PURCHASING_VIEW`, `PURCHASING_CREATE`, `PURCHASING_APPROVE` |
| Suppliers | `SUPPLIERS_VIEW` |
| Sales | `SALES_VIEW`, `SALES_CREATE`, `SALES_INVOICE`, `SALES_DISCOUNT` |
| Customers | `CUSTOMERS_VIEW` |
| Distribution | `DISTRIBUTION_VIEW`, `DISTRIBUTION_MANAGE` |
| Finance | `FINANCE_VIEW`, `FINANCE_PAYMENT`, `FINANCE_CLOSE_PERIOD` |
| Expenses | `EXPENSES_VIEW`, `EXPENSES_CREATE` |
| Employees | `EMPLOYEES_VIEW`, `EMPLOYEES_EDIT` |
| Reports | `REPORTS_VIEW`, `REPORTS_EXPORT`, `REPORTS_PRINT` |
| Chemical safety | `SAFETY_VIEW`, `SAFETY_EDIT` |
| Settings | `SETTINGS_VIEW`, `SETTINGS_EDIT` |
| Users | `USERS_VIEW`, `USERS_MANAGE` |
| Audit | `AUDIT_VIEW` |

## 3. Roles × permissions

Legend: ● granted · ○ not granted. `DefaultRoles` in the client and `database/seed/02_roles.sql`
in the API are kept identical (the client copy only drives navigation and the Users screen).

| Permission | SUPER_ADMIN | GENERAL_MANAGER | PRODUCTION_MANAGER | PRODUCTION_OPERATOR | WAREHOUSE_MANAGER | WAREHOUSE_EMPLOYEE | SALES_MANAGER | SALES_REPRESENTATIVE | ACCOUNTANT | PURCHASING_MANAGER | DELIVERY_MANAGER | VIEWER |
| --- | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-: |
| DASHBOARD_VIEW | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● |
| PRODUCTS_VIEW | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● |
| PRODUCTS_CREATE | ● | | ● | | | | | | | ● | | |
| PRODUCTS_EDIT | ● | | ● | | | | | | | | | |
| PRODUCTS_DELETE | ● | | | | | | | | | | | |
| PRODUCTS_PRICE_EDIT | ● | ● | | | | | ● | | | | | |
| MATERIALS_VIEW | ● | ● | ● | ● | ● | ● | | | | ● | | ● |
| MATERIALS_EDIT | ● | | ● | | ● | | | | | ● | | |
| FORMULATIONS_VIEW | ● | ● | ● | ● | | | | | | | | ● |
| FORMULATIONS_EDIT | ● | | ● | | | | | | | | | |
| FORMULATIONS_APPROVE | ● | ● | ● | | | | | | | | | |
| PRODUCTION_VIEW | ● | ● | ● | ● | ● | ● | | | | | | ● |
| PRODUCTION_CREATE | ● | | ● | | | | | | | | | |
| PRODUCTION_APPROVE | ● | ● | ● | | | | | | | | | |
| INVENTORY_VIEW | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● | ● |
| INVENTORY_ADJUST | ● | | | | ● | | | | | | | |
| INVENTORY_TRANSFER | ● | | | | ● | ● | | | | | | |
| PURCHASING_VIEW | ● | ● | ● | | ● | ● | | | ● | ● | | ● |
| PURCHASING_CREATE | ● | | | | | | | | | ● | | |
| PURCHASING_APPROVE | ● | ● | | | | | | | | ● | | |
| SUPPLIERS_VIEW | ● | ● | ● | | ● | | | | ● | ● | | ● |
| SALES_VIEW | ● | ● | | | ● | | ● | ● | ● | | | ● |
| SALES_CREATE | ● | | | | | | ● | ● | | | | |
| SALES_INVOICE | ● | | | | | | ● | | ● | | | |
| SALES_DISCOUNT | ● | ● | | | | | ● | | | | | |
| CUSTOMERS_VIEW | ● | ● | | | | | ● | ● | ● | | ● | ● |
| DISTRIBUTION_VIEW | ● | ● | | | ● | ● | ● | ● | | | ● | ● |
| DISTRIBUTION_MANAGE | ● | | | | ● | | | | | | ● | |
| FINANCE_VIEW | ● | ● | | | | | | | ● | | | ● |
| FINANCE_PAYMENT | ● | | | | | | | | ● | | | |
| FINANCE_CLOSE_PERIOD | ● | ● | | | | | | | ● | | | |
| EXPENSES_VIEW | ● | ● | | | | | ● | | ● | ● | ● | ● |
| EXPENSES_CREATE | ● | | | | | | | | ● | | ● | |
| EMPLOYEES_VIEW | ● | ● | | | | | | | | | | ● |
| EMPLOYEES_EDIT | ● | | | | | | | | | | | |
| REPORTS_VIEW | ● | ● | ● | | ● | | ● | | ● | ● | ● | ● |
| REPORTS_EXPORT | ● | ● | ● | | ● | | ● | | ● | ● | | |
| REPORTS_PRINT | ● | ● | ● | | ● | | ● | | ● | ● | ● | |
| SAFETY_VIEW | ● | ● | ● | ● | ● | ● | | | | ● | | ● |
| SAFETY_EDIT | ● | | ● | | ● | | | | | | | |
| SETTINGS_VIEW | ● | ● | | | | | | | | | | |
| SETTINGS_EDIT | ● | | | | | | | | | | | |
| USERS_VIEW | ● | ● | | | | | | | | | | |
| USERS_MANAGE | ● | | | | | | | | | | | |
| AUDIT_VIEW | ● | ● | | | | | | | | | | |

`SUPER_ADMIN` holds `EnumSet.allOf(Permission.class)` — a test asserts it, so a new permission
can never be silently missing from the administrator role.

## 4. Enforcement points

| Layer | Mechanism |
| --- | --- |
| API routing | every route declares its required permission; the dispatcher rejects with `403` before the controller runs |
| Service layer | ownership and state rules (e.g. only `DRAFT` can be edited, only `PENDING` can be approved) |
| Database | `role_permissions` is the source of truth; no permission hardcoded in SQL |
| Client | `NavigationManager` hides forbidden modules; actions are also checked before opening dialogs (defence in depth, never the only barrier) |
| Audit | every write stores `user_id`, `action`, `entity`, `entity_id`, `payload` and `occurred_at` |

Typical refusals:

| Situation | Response |
| --- | --- |
| No token / expired token | `401 UNAUTHORIZED` → the client returns to the login screen |
| Token valid, missing permission | `403 FORBIDDEN` |
| Editing an approved formulation | `409 CONFLICT` (`formulations.locked`) |
| Editing an issued invoice | `409 CONFLICT` (`invoices.finalized`) |
| Stock issue creating a negative balance | `422 VALIDATION` (`stock.negative`) |
| Posting into a closed accounting period | `409 CONFLICT` (`period.closed`) |

## 5. Business rules that protect the data

1. **No formulation generation.** The application stores and approves what qualified staff
   entered; it never proposes a formula, a dosage or a mixing instruction.
2. **Approved formulation = immutable.** A change requires a new version, keeping full history.
3. **Batch traceability.** Nothing enters or leaves stock without a batch reference; a batch
   cannot be sold before its quality release.
4. **FEFO.** Outbound allocation always consumes the earliest expiring released batch.
5. **No negative stock** unless the administrator explicitly enables it and the user holds
   `INVENTORY_ADJUST`.
6. **Finalised financial documents are immutable**; corrections are credit notes.
7. **Soft delete only.** `deleted_at` for master data, never a physical delete on documents,
   stock movements or audit rows.
8. **Audit trail is append-only**; the application user has no `UPDATE`/`DELETE` grant on it.

## 6. Secrets and deployment hardening

* Database credentials only in `backend/config/config.php`, which is git-ignored and lives
  outside the document root, or in environment variables.
* `.env`, `*.pem`, backups and exports are ignored by Git (`.gitignore`).
* The API sets `display_errors=Off`, logs to a file outside the web root, and returns generic
  messages for `500` errors.
* Recommended production settings: HTTPS with a valid certificate, PHP `session.cookie_*`
  unused (token auth), MySQL user limited to `SELECT/INSERT/UPDATE` on the application schema
  (no `DELETE`, no `DROP`), daily backups with a monthly restore drill.

## 7. Verification

| Check | Where |
| --- | --- |
| 12 roles and the permission matrix | `src/test/java/com/chemmanager/security/RolesTest.java` |
| Password hashing, salting, malformed hashes | `PasswordHasherTest.java` |
| Session permission aggregation | `RolesTest.sessionAggregatesPermissionsOfEveryRole` |
| Login throttling and error keys | `MockAuthServiceTest.java` |
| API-side enforcement | Phase 2 test suite (`tests/` of the PHP backend) |
