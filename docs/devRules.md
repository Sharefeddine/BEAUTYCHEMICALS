# devRules — ChemManager ERP development rules

**Source of truth:** the Google Doc `devRules`
(<https://docs.google.com/document/d/16z0tehRYX4fAeHmYVxpzpDrbG3mQ7tDVjKX6Qbk3hmM>).
This file is the versioned mirror kept in the repository so the rules travel with the code and
are reviewed in the same pull request as the change they govern. When the two disagree, the
Google Doc wins and this file is corrected in the next commit.

**Project:** chemicals beauty (Linear) · **Repository:** `Sharefeddine/BEAUTYCHEMICALS` ·
**Branch:** `arena/9682ec1e-beautychemicals`

These rules are binding for anyone — human or agent — changing this codebase. When a rule and a
shortcut disagree, the rule wins.

## 1. Product laws

1.1 The application **never generates a formulation**. No automatic formula, no suggested
dosage, no invented percentages, no mixing instructions produced by the software. It stores,
costs, freezes and archives exactly what qualified staff enter.

1.2 Trilingual by default: French (Algerian business usage), Arabic with full right-to-left
mirroring, English. Language switches at runtime without restart; Arabic mirrors the whole
layout, keeps Western digits pinned and never uses italics or uppercase for emphasis.

1.3 Currency is DZD. Dates and numbers follow the Algerian locale conventions of the chosen
language.

1.4 No hardcoded business figures. Every KPI, total, ranking and alert is aggregated from
stored transactions; an empty database produces zeros and empty states, never demo numbers.

1.5 No dead buttons and no placeholder-only screens. A control either works or is absent; a
future capability is shown by an explicit, honest placeholder that names its delivery phase.

1.6 Safety is visible: GHS pictograms, hazard classes and SDS availability travel with the raw
material wherever it appears.

## 2. Architecture laws

2.1 Desktop: Java 21 and JavaFX. The client speaks JSON over HTTP and never opens a database
connection.

2.2 Backend: PHP 8 REST API **without framework** and **without Composer**. Front controller,
router, PDO with native prepared statements, JSON envelope, explicit error handler, audit writer.

2.3 Database: MySQL 8. Every identifier — tables, columns, indexes, constraints, views,
triggers — is camelCase **without underscores**. Seeds must be idempotent (`ON DUPLICATE KEY
UPDATE` or `NOT EXISTS`).

2.4 One source of truth per concern: API paths live in `ApiEndpoints` (guarded by
`ApiContractTest` against `backend/src/routes.php`); permissions live in the `Permission` enum
mirrored by the seed; translations live in `tools/i18n/{fr,ar,en}.py` and are compiled to
`.properties`.

2.5 Business rules are enforced server-side, in the repository that owns the data, so they hold
for any caller — not only for the screen that happens to call them. The client mirrors the rule
to explain it, never to replace it.

2.6 Deletions are logical (`deletedAt`, status `CANCELLED`). The only physical `DELETE`s allowed
are the two role-matrix join tables; the application database account has no other `DELETE`
grant.

2.7 Every mutation and every session event is written to `auditLogs` with user, action, module,
entity, id, IP and severity.

## 3. Delivery process

3.1 Eight phases in order: 1 foundation → 2 database and API → 3 formulations → 4
inventory/FEFO/purchasing → 5 production → 6 sales/distribution/documents → 7 finance/payroll →
8 reports/SDS/hardening. A phase starts only when the previous one is green in CI.

3.2 CI is the only compiler. The result of record is the GitHub Actions run (jobs `desktop` and
`api`), never a local build. Any script that logs through `tee` must `set -o pipefail`,
otherwise a failure is hidden.

3.3 Every phase ends with: a report in `docs/PHASE-<n>-REPORT.md` (scope, endpoints, tests, CI
run number, deliberate deferrals), an updated `docs/04-roadmap.md`, an updated `README.md`
status block, and a Linear sync (section 7).

3.4 Deferring work is allowed; hiding it is not. A deferral is written in the report and tracked
as a Linear issue naming its target phase.

3.5 Commits are thematic and explained in the imperative: what changed and why it matters.
Pushing to the working branch is part of the loop, not the reward.

## 4. Code rules

4.1 Java: records for models, immutability where practical, never null into the UI, one service
interface per module with an API implementation and a demo implementation that obey the same
rules.

4.2 The API layer never guesses. The envelope `{success,data,meta}` is unwrapped centrally,
1-based server pages become 0-based client pages inside `ApiClient`, and row-to-model translation
lives in `ApiMapper` only.

4.3 PHP: `declare(strict_types=1)` in every file, typed parameters, **one unique named
placeholder per occurrence** (PDO native prepares reject duplicates), transactions around
multi-table writes.

4.4 SQL: never `SELECT *` in list endpoints, always explicit columns and aliases; pagination
always returns `meta {page,pageSize,total,totalPages}`; sorting falls back to a whitelist.

4.5 i18n: every user-visible string is a key present in the three bundles; the parity audit
(`tools/i18n/audit.py`) must report 0 missing before a push.

4.6 UI: follow `docs/08-ux-guidelines.md` — designed empty states, visible filters, keyboard
support (`Ctrl+F`, `Esc`), colour never the only status cue, figures right-aligned and tabular.

4.7 Styling: every colour is a token declared by **both** theme files, every font size belongs to
the nine-step scale and none is below 11 px, every interactive control paints a 2 px focus ring,
and a controller never calls `setStyle` for typography, spacing or colour — it adds a style class.
`tools/ui/checkDesign.py` and the `DesignSystemTest` fail the build otherwise. A new token is
added to both themes **and** to the contrast contract before it is used.

A screen never declares a colour, a font size or an inline style of its own: `src/main/java` and
`src/main/resources/fxml` may contain no colour literal, no `-fx-font-size`, no FXML `style=`
attribute and no `setStyle` call — the single allowed exception is the per-column alignment in
`DataTable`, which JavaFX cannot express with a class. Code that must draw outside the scene graph
(the window background, the application icon) reads the tokens through `ThemePalette`, and every
`Dialog` themes its pane with `Dialogs.applyTheme`, because a dialog builds its own window and
inherits neither the stylesheets nor the Arabic typography of the main scene.

## 5. Testing rules

5.1 Every business rule has at least one test that fails when the rule breaks. A phase cannot
close on untested behaviour.

5.2 API: unit tests for the core (router, envelope, validator, authorizer) and end-to-end HTTP
tests against a real MySQL 8 service container — never a mocked database.

5.3 Desktop: JUnit 5 for pure logic and **TestFX** for the user interface. TestFX drives the real
scene graph (clicks, typing, reads) and runs headless on CI through Xvfb, so no display is
required; UI tests assert visible behaviour, never implementation details.

5.4 The contract test is non-negotiable: `ApiContractTest` parses `backend/src/routes.php` and
fails the build when the client references a route the server does not serve.

5.5 A red build is fixed before any new feature starts. Never disable or delete a failing test to
go green.

## 6. Definition of done, per phase

- CI green on both jobs, with the run number quoted in the report.
- Integration tests cover every new endpoint, including its refusal paths (401, 403, 404, 409,
  422).
- UI reachable from the sidebar, permission-aware, FR/AR/EN strings added, Arabic layout checked.
- Audit entries written for every mutation.
- Schema check, ER-doc check and i18n audit green before the push.
- `docs/PHASE-n-REPORT.md` written, roadmap and README updated.
- Linear synced: phase issue moved to Done, project update posted, delivery-log document
  refreshed.

## 7. Linear sync procedure, after every phase

7.1 Move the phase issue (`SHA-xx`) to Done and add a comment with the CI run number and the test
counts.

7.2 Move the next phase issue to In Progress (or leave it in Todo until its first commit) and
keep its acceptance criteria current.

7.3 Post a project update on “chemicals beauty” with health `onTrack` or `atRisk` and the
delivery summary.

7.4 Refresh the project document “ChemManager ERP — delivery log and current status”: phase
table, metrics, CI run.

7.5 Open a Linear issue for anything deferred, naming its target phase. No silent debt.

## 8. Quality gates to run before a push

```bash
python3 tools/db/checkSchema.py             # 0 problems
python3 tools/db/generateErDoc.py --check   # up to date
python3 tools/i18n/build.py                 # bundles regenerated
python3 tools/i18n/audit.py                 # 0 missing keys
python3 tools/ui/checkDesign.py             # tokens, contrast, type scale, doc drift: 0 problems
# PHP syntax lint over backend/ (php -l, or the php-wasm token_get_all check used in the sandbox)
git status                                  # no build output, no credentials, no database dumps
```

## 9. Standing decisions

- Demo mode and API mode are both first-class: every `MockXxxService` implements the same rules
  as the API so the workflow can be reviewed offline.
- Every document states its own date and period: no silent default that changes a total.
- The formulation sheet PDF (FR and AR) ships with the Phase 6 document engine instead of a
  second renderer (Linear `SHA-57`).
- When a rule must be broken, the change starts with an explicit decision recorded here and in
  the phase report — never with a quiet exception in the code.
