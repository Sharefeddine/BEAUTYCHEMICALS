# ChemManager ERP — Operations Guide

Covers installation, backups, restore drills and routine maintenance. Written for the
company's IT responsible, not only for developers.

---

## 1. Topology

```
Workstations (Windows 10/11)            Application server                Database server
┌───────────────────────────┐          ┌──────────────────────┐         ┌───────────────────┐
│ ChemManager ERP client    │  HTTPS   │ PHP 8 API (Apache    │  TCP    │ MySQL 8 (InnoDB)  │
│ Java 21 + JavaFX          │ ───────► │ or nginx + php-fpm)  │ ──────► │ utf8mb4           │
│ no database driver        │  JSON    │ no Composer, PDO     │ 3306    │ daily backups     │
└───────────────────────────┘          └──────────────────────┘         └───────────────────┘
                                                  │
                                          /var/backups/chemmanager/*.sql.gz
```

The client can also run in `MOCK` mode (no server at all) for training, demonstrations and
support diagnostics — the status bar always shows which source is in use.

## 2. Workstation installation

1. Install **JDK 21** (Temurin or Oracle) — the client needs a JRE 21 with JavaFX support.
2. Copy `chemmanager-erp.jar` (or the installer built in Phase 8) to `C:\ChemManager\`.
3. Create a shortcut:
   `javaw -Ddata.mode=API -Dapi.base.url=https://erp.company.dz/api -jar C:\ChemManager\chemmanager-erp.jar`
4. First launch: sign in, open **Settings → data source** and use *Test connection* to confirm
   the API is reachable.
5. Theme, language and the remembered username are stored per Windows user
   (`java.util.prefs`), so several operators can share one workstation.

Configuration precedence: environment variable (`CHEMMANAGER_API_URL`,
`CHEMMANAGER_DATA_MODE`) → `-D` system property → `app.properties` defaults. No password is
ever stored on the workstation.

## 3. Server installation (Phase 2 onwards)

```bash
# 1. database
mysql -u root -p -e "CREATE DATABASE chemmanager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p chemmanager < database/schema/01_schema.sql
mysql -u root -p chemmanager < database/seed/01_permissions.sql
mysql -u root -p chemmanager < database/seed/02_roles.sql
# 2. application account (no DELETE, no DDL)
mysql -u root -p chemmanager -e "
  CREATE USER 'chemapp'@'10.0.0.%' IDENTIFIED BY '<strong-password>';
  GRANT SELECT, INSERT, UPDATE ON chemmanager.* TO 'chemapp'@'10.0.0.%';"
# 3. API configuration
cp backend/config/config.example.php backend/config/config.php   # then edit, keep it out of Git
chmod 640 backend/config/config.php
```

Health check: `GET https://erp.company.dz/api/health` → `{"status":"ok","database":"ok"}`.
The desktop client probes the same endpoint in `AUTO` mode.

## 4. Backups

| Item | Method | Frequency | Retention |
| --- | --- | --- | --- |
| MySQL data | `mysqldump --single-transaction --routines --triggers` → gzip | daily 02:00 | 30 days |
| MySQL weekly | same, full | Sunday 03:00 | 12 weeks |
| Uploaded files (SDS, invoices PDF) | `rsync` to the NAS | daily 02:30 | 30 versions |
| Configuration | encrypted copy of `config.php` | on change | 5 versions |

```bash
# /etc/cron.d/chemmanager-backup
0 2 * * *  root  /usr/local/bin/chemmanager-backup.sh daily
0 3 * * 0  root  /usr/local/bin/chemmanager-backup.sh weekly
```

`chemmanager-backup.sh` writes `backups.` row through the API (`SUPER_ADMIN` service token) so
successes and failures appear in the in-app notification centre; a failed backup raises a
critical notification at the next login. Backups are never stored inside the web root.

## 5. Restore drill (quarterly)

1. Provision a scratch MySQL instance.
2. `gunzip -c /var/backups/chemmanager/daily/chemmanager-YYYY-MM-DD.sql.gz | mysql -u root -p chemtest`.
3. Point a test client at the staging API (`-Dapi.base.url=http://staging/api`) and verify:
   invoice numbering continuity, stock balances equal the sum of movements, the last 30 audit
   entries exist, the dashboard totals match production for the same day.
4. Record the drill in the operations log with date, duration and anomalies.

Recovery targets: RPO 24 h (daily dump), RTO 2 h (restore + API restart + client relaunch).

## 6. Routine maintenance

| Task | Frequency | Notes |
| --- | --- | --- |
| Check notification centre for failed backups | daily | shown to administrators at login |
| Expired SDS / batches report | weekly | Reports → chemical safety (Phase 8) |
| Table statistics / index review | monthly | `ANALYZE TABLE` on the largest transactional tables |
| Purge of expired `api_tokens` | weekly | cron on the API server |
| Archiving of closed accounting periods | yearly | read-only export, data stays queryable |
| Client update rollout | on release | replace the jar, keep the previous version for one week |

## 7. Incident playbook

| Symptom | First checks | Action |
| --- | --- | --- |
| "Server unreachable" in the client | `GET /api/health`, service status, firewall | restart php-fpm/Apache; the client keeps working read-only in `AUTO` mode only if it was started in that mode |
| Login refused for everyone | MySQL up? `users.is_active`? clock skew on the token validity | restore the database service, then ask users to sign in again |
| Numbers do not match the paperwork | data source indicator in the status bar (demo vs API) | the client may be running in `MOCK`; switch in Settings |
| Two users edit the same document | `409 CONFLICT` response, audit log | the second user reloads the document; the API never merges silently |
| A batch is missing from stock | `stock_movements` for that batch, `quality_status` | quarantined batches are invisible to sales by design |
| Performance degradation | slow query log, index usage, table sizes | add the missing index through a versioned migration, never by hand |

## 8. Logging and monitoring

* API logs: `/var/log/chemmanager/api-YYYY-MM-DD.log` (rotated daily, 30 days), one line per
  request with user, route, status, duration; no password or token is ever logged.
* Audit log: `audit_logs` table, append-only, queried from the Users & Permissions module.
* Client logs: `%APPDATA%\ChemManager\logs\client.log` (rotated, 10 MB), written on unexpected
  exceptions only — it contains no business data beyond identifiers.
* Health endpoint monitored every 5 minutes; three consecutive failures raise an alert to the
  administrator's e-mail.

## 9. Security operations

* Rotate API tokens by signing out everywhere (`POST /api/auth/logout-all`) after any suspicion.
* Revoke a departing employee's account immediately (`users.is_active = 0`), which invalidates
  every active token at the next request.
* Keep the schema migration history in Git and review every change; no manual `UPDATE` on
  production data without an audit ticket.
* Apply MySQL and PHP security updates monthly; keep the JDK updated on workstations.
