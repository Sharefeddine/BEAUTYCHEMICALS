# ChemManager ERP — Architecture

Version 1.0 · Phase 1 · scope: desktop client foundation + document set

---

## 1. Goals and constraints

| Requirement | Architectural consequence |
| --- | --- |
| Desktop application, rich tables and charts | JavaFX 21 desktop client, FXML screens, CSS design system |
| Server-side security, no DB credentials on the client | Client talks only to a PHP REST API; MySQL is reachable from the web server only |
| Batch traceability, FEFO, no negative stock | Batch-centric inventory model; stock movements are append-only rows, quantities derived |
| Approved formulations are immutable | Status machine on `formulations`, edit forbidden once `APPROVED`, new change = new version |
| Never delete financial or stock history | Soft delete (`deleted_at`, `is_active`) everywhere; no `DELETE` on documents |
| Arabic RTL + French + English at runtime | `I18n` locale property + `NodeOrientation` on the scene graph, translation bundles |
| Runs without infrastructure (demo) | `data.mode = MOCK / API / AUTO` behind service interfaces |
| No chemical formulation generation | The application stores and approves formulas entered by qualified staff only |

## 2. Layered architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│ Presentation      FXML screens · controllers · reusable components (CSS)  │
├───────────────────────────────────────────────────────────────────────────┤
│ Application       AppContext (stage, session, navigation, theme, locale)  │
├───────────────────────────────────────────────────────────────────────────┤
│ Service           AuthService · DashboardService · ProductService · …     │
│                   ← the only API used by controllers (interfaces)         │
├───────────────────────────────────────────────────────────────────────────┤
│ Data access       Mock…Service (demo arrays)   Api…Service (REST client)  │
│                   ServiceFactory chooses one from data.mode               │
├───────────────────────────────────────────────────────────────────────────┤
│ Transport         ApiClient (java.net.http) · Jackson · ApiEndpoints      │
└───────────────────────────────────────────────────────────────────────────┘
                                     ⇅ HTTP/JSON (bearer token)
┌───────────────────────────────────────────────────────────────────────────┐
│ PHP API (phase 2+)  Router → middleware (auth, RBAC, rate limit, CORS)    │
│                     → controllers → services → repositories (PDO)         │
├───────────────────────────────────────────────────────────────────────────┤
│ MySQL 8             transactional tables, audit log, batch ledger         │
└───────────────────────────────────────────────────────────────────────────┘
```

**Rules enforced by the code**

1. Controllers depend on **interfaces** in `service/`, never on a concrete implementation.
2. `ServiceFactory` is the single place that decides between `MOCK` and `API`.
3. Business rules that must survive a UI rewrite live in the service layer *and* are
   re-validated by the API: the client is never the authority on permissions or stock levels.
4. Jackson (`JsonMapper`) is configured once (JavaTimeModule, ISO-8601, unknown properties
   ignored) and shared by every API service.
5. Every call that can take longer than a frame runs in `FxTasks` (background thread + UI
   thread callback), with busy state and translated error reporting (`ApiException` → key).

## 3. Package map (client)

| Package | Responsibility |
| --- | --- |
| `app` | `ChemManagerApp` (JavaFX `Application`, composition root), `AppContext` (stage, session, navigation, locale, theme) |
| `config` | `AppConfig` (properties + system props + env), `ThemeManager`, `UserPreferences` (`java.util.prefs`), `Theme`, `DataMode` |
| `localization` | `I18n` — current `Locale` as an observable property, RTL flag, `MessageFormat` arguments, missing-key markers |
| `security` | `Permission` (44 values), `Role`, `DefaultRoles` (12 roles), `Session`, `SessionContext`, `PasswordHasher` (PBKDF2-SHA256, 120k iterations) |
| `model` | Immutable domain records (`Product`, `RawMaterial`, `User`, `DateRange`, `Severity`, `NotificationItem`, `CompanySettings`) + `model.analytics` (dashboard aggregates) |
| `dto` | Transport objects: `PagedResult`, `ProductQuery`, `MaterialQuery`, `LoginRequest/Response`, `DashboardRequest`, `ApiError`, `NotificationDto` |
| `validation` | Fluent `Validator` returning `ValidationResult` (field → translated message) |
| `api` | `ApiClient` (timeout, bearer token, error mapping), `ApiEndpoints`, `ApiException`, `JsonMapper` |
| `repository` | `MockDataStore` (deterministic demo dataset: 2 years of sales/purchases/expenses/production), settings repositories |
| `service` | 9 interfaces + `mock/` and `api/` implementations + `ServiceFactory` |
| `component` | `KpiCard`, `Card`, `DataTable`, `ChartFactory`, `FormFields`, `StatusBadge`, `Toasts`, `Dialogs`, `ModulePlaceholder`, `Icons` |
| `navigation` | `NavGroup` (8 sections), `NavigationModule` (18 modules: id, icon, FXML, permission, phase, availability), `NavigationManager` (load, permission check, resource bundle) |
| `controller` | `MainWindowController` (shell), `auth/LoginController`, `dashboard/`, and one package per module |
| `util` | `Formatters`, `Money`, `DateRanges`, `FxTasks`, `Paging`, `ExcelExporter`, `Labels`, `AppIcon` |

## 4. Maven structure

```xml
<properties>
  maven.compiler.release = 21      javafx.version = 21.0.4
  jackson.version = 2.17.2         poi.version = 5.2.5
  junit.version = 5.10.2           main.class = com.chemmanager.app.ChemManagerApp
</properties>
```

* `maven-compiler-plugin` 3.13.0 — `release 21`, UTF-8 (Arabic/French literals in tests).
* `javafx-controls` + `javafx-fxml` (the Maven plugin resolves the platform-specific natives).
* `javafx-maven-plugin` 0.0.8 — `mvn javafx:run`.
* `maven-surefire-plugin` 3.2.5 — JUnit 5, `-Duser.language=en` for deterministic formatting.
* `maven-jar-plugin` — executable jar with `Main-Class` in the manifest.

## 5. Startup sequence

```
main() → ChemManagerApp.start(stage)
   → AppConfig.load()             (app.properties ← system properties ← environment)
   → I18n.get()                   (locale from UserPreferences, bundle loaded)
   → ThemeManager.bind(scene)     (app.css + theme-*.css)
   → AppContext.boot(stage)       → showLogin()
                                    LoginController → AuthService.login(...)
                                      → Session + SessionContext.set(...)
   → AppContext.showShell()       → main-window.fxml
                                    MainWindowController: sidebar (permission filtered),
                                    header (title, search, notifications, theme, language,
                                    user menu), status bar, content host
                                    → NavigationManager.navigate(initial module)
```

`AppContext` owns the `Stage`; controllers never create windows. Logging out clears the
session, the navigation state and the cached services, then returns to the login screen.

## 6. Navigation and permissions

`NavigationModule` is the single source of truth for the sidebar: id, group, icon, FXML path,
required `Permission`, delivery phase and `implemented` flag.

* The sidebar is **filtered by the session's effective permissions**; a user without
  `FINANCE_VIEW` does not see the finance entry at all.
* `NavigationManager.navigate(module)` re-checks the permission (defence in depth), loads the
  FXML with the current `ResourceBundle`, replaces the content host with a fade transition and
  calls `refresh()` when the locale or theme changes.
* Modules that are not implemented yet resolve to `ModulePlaceholder`, which lists the planned
  capabilities, the phase and a link to the current section of the roadmap.

## 7. Data access strategies

| Mode | Class set | Use |
| --- | --- | --- |
| `MOCK` | `service/mock/*` + `MockDataStore` | demo, development, training, CI |
| `API` | `service/api/*` + `ApiClient` | production against PHP/MySQL |
| `AUTO` | probe `GET /health` at start-up | laptop usage against a sometimes-offline server |

All three modes implement the same interfaces, so the dashboard, tables and dialogs are
identical; only the origin of the rows changes. The status bar always displays which source
is active, and the Settings screen can switch it at runtime.

## 8. Configuration and secrets

* `src/main/resources/app.properties` holds **defaults only** (URL, timeouts, currency,
  theme, locale, data mode) and is safe to commit.
* Precedence: environment variable → JVM system property → `app.properties`.
* `backend/config/config.php` (API database credentials) is listed in `.gitignore` and never
  committed; the PHP API reads it from the web root outside the repository.
* The client never stores a database password. Session tokens are kept in memory only.

## 9. Concurrency model

* All service calls are asynchronous (`FxTasks.run`): a background thread performs the call,
  success/failure callbacks run on the JavaFX application thread.
* Long operations show an inline busy state instead of blocking the UI thread; the login
  screen disables the submit button and shows a progress indicator.
* Mock services read immutable snapshots, so no locking is required; the API client is
  thread-safe (`HttpClient` is shared, token read through a volatile field).

## 10. Error handling

| Layer | Behaviour |
| --- | --- |
| Validation | `Validator` collects field errors, shows them inline; the action is blocked |
| Service | throws `AuthException` / `AccessDeniedException` / `ApiException` carrying an i18n key |
| Controller | catches, translates the key, shows a `Toasts` message or a `Dialogs.error` dialog |
| API | always answers `{ "success": false, "error": { "code", "message" } }` with a proper status |

No stack trace is ever shown to the user; unexpected exceptions are logged with context and
reported as `error.unknown`.
