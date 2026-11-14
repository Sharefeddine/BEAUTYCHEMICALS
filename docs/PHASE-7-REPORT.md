# Phase 7 — Finance, expenses, employees, payroll and the statements

> Scope: the accounting calendar and its closing guard, the cash book fed by every settlement, the
> operating expenses with their approval and payment, the staff file with attendance, leave and the
> monthly payroll booked as an expense, and the three statements the accountant signs — on both
> halves (PHP API + JavaFX desktop), verified in CI against a real MySQL 8 database.

**Guiding rule:** the treasury has exactly one source of truth. Money moves through the cash book,
and the cash book is written by the documents that caused the movement — a customer payment, a paid
expense, a paid salary — inside the same transaction that records the fact. Nothing posts into a
closed month, and a month is only reopened by a super administrator.

---

## 1. Data model used

The tables existed since Phase 2; this phase gives them behaviour. **No schema change**: 68 tables,
schema checker still reports 0 problems.

| Table | Role in this phase |
| --- | --- |
| `accountingPeriods` | one row per month, `OPEN \| CLOSED`, `closedBy`, `closedAt` |
| `cashTransactions` | the cash book: `direction` (`IN \| OUT`), `amount`, `sourceType` (`INVOICE \| PAYMENT \| SUPPLIERPAYMENT \| EXPENSE \| SALARY \| TRANSFER \| OTHER`), `sourceId`, `occurredAt`, `accountingPeriodId` |
| `cashAccounts` | `CAISSE`, `BANQUE` — `openingBalance`, `isActive` |
| `expenseCategories` | 10 seeded categories, `isSalary` and `isVehicle` flags drive the forms |
| `expenses` | `DP-00001`, `DRAFT → APPROVED → PAID \| CANCELLED`, `amount` + `vatAmount`, `paymentMethod`, `cashAccountId`, `supplierId` / `vehicleId` / `employeeId`, `approvedBy` |
| `employees` | `EMP-0001`, department, position, contract, `baseSalary` |
| `attendance` | one row per employee and day (`uqAttendanceEmployeeDay`), `PRESENT \| ABSENT \| LEAVE \| SICK \| HOLIDAY \| SITE` |
| `leaveRequests` | `REQUESTED → APPROVED \| REJECTED \| CANCELLED`, `approvedBy` / `approvedAt` |
| `salaryPayments` | one row per employee and month, `netAmount` generated (`base + bonus − deductions − advance`), `paidAt`, `expenseId` |
| `invoices` / `invoiceItems` / `creditNotes` | read side of the statements: issued invoices minus issued or applied credit notes |
| `auditLogs` | every state change is logged with its document number (`CREATE`, `APPROVE`, `PAY`, `CANCEL`, `UPDATE`, `DELETE`) |

## 2. API — 34 new routes (156 in total)

| Area | Routes | Permission |
| --- | --- | --- |
| Accounting calendar | `GET /api/finance/periods`, `GET /api/finance/periods/{id}`, `POST /{id}/close`, `POST /{id}/reopen` | `FINANCE_VIEW` / `FINANCE_CLOSE_PERIOD`, reopening also requires the `SUPER_ADMIN` role |
| Cash book | `GET /api/finance/cashbook` (search, account, direction, source type, window, paging), `GET /api/finance/summary`, `POST /api/finance/cashTransactions` | `FINANCE_VIEW` / `FINANCE_PAYMENT` |
| Expenses | `GET /api/expenseCategories`, `GET /api/expenses`, `GET /api/expenses/summary`, `GET /api/expenses/{id}`, `POST /api/expenses`, `POST /{id}/approve`, `POST /{id}/pay`, `POST /{id}/cancel` | `EXPENSES_VIEW` / `EXPENSES_CREATE`, payment needs `FINANCE_PAYMENT` |
| Staff | `GET /api/departments`, `GET /api/employees`, `GET /api/employees/{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`, `GET /{id}/attendance`, `POST /{id}/attendance` | `EMPLOYEES_VIEW` / `EMPLOYEES_EDIT` |
| Leave | `GET /api/leaveRequests`, `POST /api/leaveRequests`, `POST /{id}/approve`, `POST /{id}/reject`, `POST /{id}/cancel` | `EMPLOYEES_VIEW` / `EMPLOYEES_EDIT` |
| Payroll | `GET /api/salaryPayments`, `POST /api/salaryPayments/run`, `POST /api/salaryPayments/{id}/pay` | `EMPLOYEES_VIEW` / `EMPLOYEES_EDIT`, payment needs `FINANCE_PAYMENT` |
| Statements | `GET /api/reports/margin`, `GET /api/reports/cashflow`, `GET /api/reports/result` | `REPORTS_VIEW` |

Error codes introduced: `PERIOD_CLOSED`, `PERIOD_ALREADY_CLOSED`, `CASH_ACCOUNT_INACTIVE`,
`INVALID_STATUS`. Validation errors keep the `{field: rule}` shape the client already translates.

## 3. The rules this phase implements

**The calendar guards the books.** `FinanceRepository::record()` resolves the period of the movement
from its `occurredAt`, and refuses it with `409 PERIOD_CLOSED` when the month is closed. Because every
posting path (payment, expense payment, salary payment, manual movement) goes through it, there is no
back door: the expense of a closed month stays `APPROVED` and its cash line is never written.

**Reopening is a super-administrator gesture.** Closing needs `FINANCE_CLOSE_PERIOD` — the accountant
has it. Reopening needs that permission **and** the `SUPER_ADMIN` role, checked in
`FinanceController::reopenPeriod()` from the roles recomputed on the request, not from the token. An
ACCOUNTANT closes a month and is refused the reopening with `403 FORBIDDEN`; the admin reopens it.
Both attempts are audited.

**An expense walks a one-way street.** `DRAFT → APPROVED → PAID`; a paid expense cannot be cancelled
(`INVALID_STATUS`) because money left the till — it is corrected by an opposite entry. Payment is a
single transaction: the expense becomes `PAID`, the cash line is written, and if the month is closed
the whole thing rolls back.

**Payroll settles through the expenses.** `POST /api/salaryPayments/run` generates one line per active
employee for a month, refuses a closed month, and is idempotent (a second run creates nothing).
`POST /api/salaryPayments/{id}/pay` books a `PAID` expense in the `SALARIES` category, links it back
through `salaryPayments.expenseId`, and pays it — so the treasury moves by exactly the net salary and
the payroll appears in the expense summary of the month, once.

**The statements come from documents, not from memory.** Margin per product = issued invoices minus
issued or applied credit notes, costed at the product cost price, ranked by margin, with a by-category
roll-up. Cash flow = the cash book grouped by month and by source. Period result =
revenue − cost of goods − operating expenses, with the expenses detailed per category.

## 4. Desktop client — four real screens

| Screen | What it shows | What it lets the user do |
| --- | --- | --- |
| **Finance** (`finance.fxml`) | five treasury indicators (balance, in, out, receivable, payable), the accounting calendar as chips, the cash book | filter by direction and account, post a manual movement, close a month, reopen one (gated) |
| **Expenses** (`expenses.fxml`) | the list with its status badges, the detail card, the spend per category | enter a draft, approve it, pay it through a chosen cash account, cancel a draft |
| **Employees** (`employees.fxml`) | headcount, active staff, payroll mass, the staff file, the leave desk, the payroll of the selected month | hire, mark the day, request and decide leave, run the payroll, pay a salary |
| **Reports** (`reports.fxml`) | the four indicators of the month, margin by product, cash flow by month and source, the result with its expense categories | change the window and the month |

`FinanceService` has two implementations, as every module before it: `ApiFinanceService` for the real
backend and `MockFinanceService` for the offline demo — the latter replays the same rules (closed
month, paid expense, payroll), so the client never shows a screen that the server would refuse.

204 new keys in FR/EN/AR (1 285 keys per bundle, parity green), new `Labels` helpers for the expense,
period, leave, attendance, contract, cash-source and direction vocabularies, and 14 new
`ApiEndpoints` constants guarded by `ApiContractTest` against `routes.php`.

## 5. Verification

| Gate | Result |
| --- | --- |
| PHP lint | clean |
| Unit tests | 25 green |
| Integration tests (MySQL 8) | **439 checks green** (347 at the end of Phase 6) |
| Desktop tests | **99 green** (79 at the end of Phase 6) |
| Schema checker | 68 tables, 0 problems |
| i18n parity + audit | 3 bundles × 1 285 keys, 0 missing |
| `ApiContractTest` | 14 new paths verified against `routes.php` |

New desktop tests: `FinanceServiceMockTest` (9 rules — closed month, expense lifecycle, payroll run
and payment, attendance upsert, leave decisions, statement arithmetic, cash-book filtering),
`FinanceUiTest` (3 — indicators, cash book, direction filter, close and reopen a month),
`ExpensesUiTest` (3 — list and summary, buttons that follow the state, approving a draft),
`EmployeesUiTest` (3 — staff file, payroll run then payment, deciding a leave), `ReportsUiTest`
(2 — the three statements rendered, the result shown as computed).

New API checks cover: the seeded February–October closed months, the treasury reconciling with its
own lines, a closed month refusing a posting and a payroll run, the expense of a closed month staying
`APPROVED`, the cash-book filters (account, direction, source type, day), the salary booked once and
paid once, the treasury moving by exactly the net salary, the month summary per category, the
super-administrator rule on reopening, and the RBAC of every write.

## 6. Acceptance criteria

| Criterion | Evidence |
| --- | --- |
| No posting is possible in a closed period | `POST /api/finance/cashTransactions` into a closed month → `409 PERIOD_CLOSED`; the same for an expense payment and a payroll run; the expense stays `APPROVED` |
| The cash book reconciles with payments and expenses | every settlement path writes its line through `FinanceRepository::record()` in the same transaction; each account balance equals `opening + in − out` and the summary totals equal the sum of the accounts |
| A closed period cannot be reopened without super-admin action | an ACCOUNTANT closes (200) and is refused the reopen (403); only the admin reopens (200) |

## 7. What Phase 8 inherits

The reports screen is live with the three financial statements; Phase 8 adds the operational reports
(sales, purchases, production, stock, expiry, customer statements, safety registers) and the Excel
exports on top of it. The remaining planned modules are **Safety** and the hardening pass.
