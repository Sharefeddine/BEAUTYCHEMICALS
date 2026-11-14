package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.CashQuery;
import com.chemmanager.dto.EmployeeQuery;
import com.chemmanager.dto.ExpenseQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.finance.AccountingPeriod;
import com.chemmanager.model.finance.AttendanceRecord;
import com.chemmanager.model.finance.CashTransaction;
import com.chemmanager.model.finance.CashflowReport;
import com.chemmanager.model.finance.EmployeeRecord;
import com.chemmanager.model.finance.ExpenseCategory;
import com.chemmanager.model.finance.ExpenseRecord;
import com.chemmanager.model.finance.IncomeStatement;
import com.chemmanager.model.finance.LeaveRecord;
import com.chemmanager.model.finance.MarginReport;
import com.chemmanager.model.finance.SalaryLine;
import com.chemmanager.model.finance.Treasury;
import com.chemmanager.service.FinanceService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The finance half of the client, served by {@code /api/finance}, {@code /api/expenses},
 * {@code /api/employees}, {@code /api/salaryPayments} and {@code /api/reports}.
 */
public final class ApiFinanceService implements FinanceService {

    private final ApiClient client;

    public ApiFinanceService(ApiClient client) {
        this.client = client;
    }

    // ------------------------------------------------------------------ treasury and calendar

    @Override
    public Treasury treasury() {
        return treasury(client.get(ApiEndpoints.FINANCE_SUMMARY, JsonNode.class));
    }

    @Override
    public List<AccountingPeriod> periods() {
        JsonNode node = client.get(ApiEndpoints.FINANCE_PERIODS, JsonNode.class);
        List<AccountingPeriod> periods = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode row : node) {
                periods.add(period(row));
            }
        }

        return periods;
    }

    @Override
    public AccountingPeriod closePeriod(long id) {
        return period(client.post(ApiEndpoints.FINANCE_PERIODS + "/" + id + "/close", Map.of(), JsonNode.class));
    }

    @Override
    public AccountingPeriod reopenPeriod(long id) {
        return period(client.post(ApiEndpoints.FINANCE_PERIODS + "/" + id + "/reopen", Map.of(), JsonNode.class));
    }

    @Override
    public PagedResult<CashTransaction> cashbook(CashQuery query) {
        CashQuery effective = query == null ? CashQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search() == null ? "" : effective.search());
        params.put("direction", effective.direction() == null ? "" : effective.direction());
        if (effective.cashAccountId() > 0) {
            params.put("cashAccountId", Long.toString(effective.cashAccountId()));
        }
        if (effective.from() != null) {
            params.put("from", effective.from().toString());
        }
        if (effective.to() != null) {
            params.put("to", effective.to().toString());
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.FINANCE_CASHBOOK, params, JsonNode.class)
                .map(ApiFinanceService::cashTransaction);
    }

    @Override
    public CashTransaction postCashMovement(NewCashMovement movement) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cashAccountId", movement.cashAccountId());
        body.put("direction", movement.direction());
        body.put("amount", movement.amount());
        body.put("sourceType", "OTHER");
        body.put("description", movement.description() == null ? "" : movement.description());
        if (movement.occurredAt() != null) {
            body.put("occurredAt", movement.occurredAt().toString());
        }

        return cashTransaction(client.post(ApiEndpoints.FINANCE_CASH_TRANSACTIONS, body, JsonNode.class));
    }

    // ------------------------------------------------------------------ expenses

    @Override
    public List<ExpenseCategory> expenseCategories() {
        JsonNode node = client.get(ApiEndpoints.EXPENSE_CATEGORIES, JsonNode.class);
        List<ExpenseCategory> categories = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode row : node) {
                categories.add(new ExpenseCategory(row.path("id").asLong(), ApiSalesService.text(row, "code"),
                        ApiSalesService.text(row, "nameFr"), ApiSalesService.text(row, "nameAr"),
                        row.path("isSalary").asBoolean(), row.path("isVehicle").asBoolean(),
                        row.path("isActive").asBoolean(true)));
            }
        }

        return categories;
    }

    @Override
    public PagedResult<ExpenseRecord> expenses(ExpenseQuery query) {
        ExpenseQuery effective = query == null ? ExpenseQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search() == null ? "" : effective.search());
        params.put("status", effective.status() == null ? "" : effective.status());
        if (effective.categoryId() > 0) {
            params.put("categoryId", Long.toString(effective.categoryId()));
        }
        if (effective.from() != null) {
            params.put("from", effective.from().toString());
        }
        if (effective.to() != null) {
            params.put("to", effective.to().toString());
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.EXPENSES, params, JsonNode.class).map(ApiFinanceService::expense);
    }

    @Override
    public ExpenseRecord createExpense(NewExpense expense) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("categoryId", expense.categoryId());
        body.put("label", expense.label());
        body.put("amount", expense.amount());
        body.put("vatAmount", expense.vatAmount() == null ? BigDecimal.ZERO : expense.vatAmount());
        body.put("expenseDate", (expense.expenseDate() == null ? LocalDate.now() : expense.expenseDate()).toString());
        body.put("paymentMethod", expense.paymentMethod());
        if (expense.cashAccountId() > 0) {
            body.put("cashAccountId", expense.cashAccountId());
        }
        body.put("notes", expense.notes() == null ? "" : expense.notes());

        return expense(client.post(ApiEndpoints.EXPENSES, body, JsonNode.class));
    }

    @Override
    public ExpenseRecord approveExpense(long id) {
        return expense(client.post(ApiEndpoints.EXPENSES + "/" + id + "/approve", Map.of(), JsonNode.class));
    }

    @Override
    public ExpenseRecord payExpense(long id, long cashAccountId) {
        return expense(client.post(ApiEndpoints.EXPENSES + "/" + id + "/pay",
                Map.of("cashAccountId", cashAccountId), JsonNode.class));
    }

    @Override
    public ExpenseRecord cancelExpense(long id) {
        return expense(client.post(ApiEndpoints.EXPENSES + "/" + id + "/cancel", Map.of(), JsonNode.class));
    }

    @Override
    public ExpenseSummary expenseSummary(LocalDate from, LocalDate to) {
        Map<String, String> params = new LinkedHashMap<>();
        if (from != null) {
            params.put("from", from.toString());
        }
        if (to != null) {
            params.put("to", to.toString());
        }
        JsonNode node = client.get(ApiEndpoints.EXPENSES_SUMMARY, params, JsonNode.class);
        List<IncomeStatement.CategoryExpense> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (node != null && node.isArray()) {
            for (JsonNode row : node) {
                BigDecimal amount = ApiSalesService.decimal(row, "amount");
                BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
                total = total.add(value);
                rows.add(new IncomeStatement.CategoryExpense(ApiSalesService.text(row, "code"),
                        ApiSalesService.text(row, "nameFr"), value));
            }
        }

        return new ExpenseSummary(total, rows);
    }

    // ------------------------------------------------------------------ staff and payroll

    @Override
    public PagedResult<EmployeeRecord> employees(EmployeeQuery query) {
        EmployeeQuery effective = query == null ? EmployeeQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search() == null ? "" : effective.search());
        if (effective.departmentId() > 0) {
            params.put("departmentId", Long.toString(effective.departmentId()));
        }
        if (effective.activeOnly() != null) {
            params.put("activeOnly", effective.activeOnly() ? "1" : "0");
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.EMPLOYEES, params, JsonNode.class).map(ApiFinanceService::employee);
    }

    @Override
    public EmployeeRecord createEmployee(NewEmployee employee) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", employee.firstName());
        body.put("lastName", employee.lastName());
        if (employee.departmentId() > 0) {
            body.put("departmentId", employee.departmentId());
        }
        body.put("position", employee.position() == null ? "" : employee.position());
        body.put("phone", employee.phone() == null ? "" : employee.phone());
        if (employee.hireDate() != null) {
            body.put("hireDate", employee.hireDate().toString());
        }
        body.put("contractType", employee.contractType() == null ? "CDI" : employee.contractType());
        body.put("baseSalary", employee.baseSalary() == null ? BigDecimal.ZERO : employee.baseSalary());

        return employee(client.post(ApiEndpoints.EMPLOYEES, body, JsonNode.class));
    }

    @Override
    public EmployeeRecord deactivateEmployee(long id) {
        client.delete(ApiEndpoints.EMPLOYEES + "/" + id);

        return new EmployeeRecord(id, "", "", "", "", "", "", null, "", BigDecimal.ZERO, false, null);
    }

    @Override
    public List<AttendanceRecord> attendance(long employeeId, LocalDate from, LocalDate to) {
        Map<String, String> params = new LinkedHashMap<>();
        if (from != null) {
            params.put("from", from.toString());
        }
        if (to != null) {
            params.put("to", to.toString());
        }
        JsonNode node = client.get(ApiEndpoints.EMPLOYEES + "/" + employeeId + "/attendance", params, JsonNode.class);
        List<AttendanceRecord> rows = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode row : node) {
                rows.add(new AttendanceRecord(row.path("id").asLong(), ApiSalesService.date(row, "workDate"),
                        ApiSalesService.text(row, "status"), ApiSalesService.text(row, "checkIn"),
                        ApiSalesService.text(row, "checkOut"), ApiSalesService.text(row, "notes")));
            }
        }

        return rows;
    }

    @Override
    public AttendanceRecord recordAttendance(long employeeId, LocalDate workDate, String status, String checkIn,
                                             String checkOut) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workDate", (workDate == null ? LocalDate.now() : workDate).toString());
        body.put("status", status);
        if (checkIn != null && !checkIn.isBlank()) {
            body.put("checkIn", checkIn);
        }
        if (checkOut != null && !checkOut.isBlank()) {
            body.put("checkOut", checkOut);
        }
        JsonNode row = client.post(ApiEndpoints.EMPLOYEES + "/" + employeeId + "/attendance", body, JsonNode.class);

        return new AttendanceRecord(row.path("id").asLong(), ApiSalesService.date(row, "workDate"),
                ApiSalesService.text(row, "status"), ApiSalesService.text(row, "checkIn"),
                ApiSalesService.text(row, "checkOut"), ApiSalesService.text(row, "notes"));
    }

    @Override
    public PagedResult<LeaveRecord> leaveRequests(String status) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("status", status == null ? "" : status);
        params.put("page", "1");
        params.put("pageSize", "50");

        return client.getPage(ApiEndpoints.LEAVE_REQUESTS, params, JsonNode.class).map(ApiFinanceService::leave);
    }

    @Override
    public LeaveRecord requestLeave(long employeeId, String leaveType, LocalDate startDate, LocalDate endDate,
                                    String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("employeeId", employeeId);
        body.put("leaveType", leaveType);
        body.put("startDate", startDate == null ? LocalDate.now().toString() : startDate.toString());
        body.put("endDate", endDate == null ? LocalDate.now().toString() : endDate.toString());
        body.put("reason", reason == null ? "" : reason);

        return leave(client.post(ApiEndpoints.LEAVE_REQUESTS, body, JsonNode.class));
    }

    @Override
    public LeaveRecord decideLeave(long id, Decision decision) {
        String path = ApiEndpoints.LEAVE_REQUESTS + "/" + id + "/" + decision.name().toLowerCase();

        return leave(client.post(path, Map.of(), JsonNode.class));
    }

    @Override
    public PagedResult<SalaryLine> salaryPayments(String period) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("period", period == null ? "" : period);
        params.put("page", "1");
        params.put("pageSize", "200");

        return client.getPage(ApiEndpoints.SALARY_PAYMENTS, params, JsonNode.class).map(ApiFinanceService::salary);
    }

    @Override
    public int runPayroll(String period) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("period", period);
        JsonNode node = client.post(ApiEndpoints.SALARY_PAYMENTS + "/run", body, JsonNode.class);

        return node.path("created").asInt();
    }

    @Override
    public SalaryLine paySalary(long id, long cashAccountId) {
        JsonNode node = client.post(ApiEndpoints.SALARY_PAYMENTS + "/" + id + "/pay",
                Map.of("cashAccountId", cashAccountId), JsonNode.class);

        return new SalaryLine(node.path("id").asLong(), node.path("employeeId").asLong(), "", "",
                ApiSalesService.text(node, "period"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, ApiSalesService.decimal(node, "netAmount"), true, null);
    }

    // ------------------------------------------------------------------ statements

    @Override
    public MarginReport margin(LocalDate from, LocalDate to) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("from", from == null ? "" : from.toString());
        params.put("to", to == null ? "" : to.toString());
        JsonNode node = client.get(ApiEndpoints.REPORTS_MARGIN, params, JsonNode.class);

        return margin(node);
    }

    @Override
    public CashflowReport cashflow(LocalDate from, LocalDate to) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("from", from == null ? "" : from.toString());
        params.put("to", to == null ? "" : to.toString());
        JsonNode node = client.get(ApiEndpoints.REPORTS_CASHFLOW, params, JsonNode.class);

        return cashflow(node);
    }

    @Override
    public IncomeStatement result(String period) {
        JsonNode node = client.get(ApiEndpoints.REPORTS_RESULT, Map.of("period", period), JsonNode.class);

        return statement(node);
    }

    // ------------------------------------------------------------------ mapping

    private Treasury treasury(JsonNode node) {
        JsonNode body = node == null ? null : node.path("treasury");
        List<Treasury.Account> accounts = new ArrayList<>();
        if (body != null && body.path("accounts").isArray()) {
            for (JsonNode row : body.path("accounts")) {
                accounts.add(new Treasury.Account(row.path("id").asLong(), ApiSalesService.text(row, "code"),
                        ApiSalesService.text(row, "name"), ApiSalesService.text(row, "type"),
                        ApiSalesService.decimal(row, "openingBalance"), ApiSalesService.decimal(row, "totalIn"),
                        ApiSalesService.decimal(row, "totalOut"), ApiSalesService.decimal(row, "balance")));
            }
        }
        BigDecimal balance = body == null ? BigDecimal.ZERO : ApiSalesService.decimal(body.path("totals"), "balance");

        return new Treasury(accounts, balance, ApiSalesService.decimal(node, "income"),
                ApiSalesService.decimal(node, "expense"), ApiSalesService.decimal(node, "receivable"),
                ApiSalesService.decimal(node, "payable"));
    }

    private static AccountingPeriod period(JsonNode row) {
        return new AccountingPeriod(row.path("id").asLong(), ApiSalesService.text(row, "code"),
                ApiSalesService.date(row, "startDate"), ApiSalesService.date(row, "endDate"),
                ApiSalesService.text(row, "status"), ApiSalesService.text(row, "closedByName"),
                dateTime(row, "closedAt"), row.path("transactionCount").asInt());
    }

    private static CashTransaction cashTransaction(JsonNode row) {
        return new CashTransaction(row.path("id").asLong(), row.path("cashAccountId").asLong(),
                ApiSalesService.text(row, "accountCode"), ApiSalesService.text(row, "accountName"),
                ApiSalesService.text(row, "direction"), ApiSalesService.decimal(row, "amount"),
                ApiSalesService.decimal(row, "signedAmount"), ApiSalesService.text(row, "sourceType"),
                ApiSalesService.text(row, "description"), dateTime(row, "occurredAt"),
                ApiSalesService.text(row, "periodCode"), ApiSalesService.text(row, "userName"));
    }

    private static ExpenseRecord expense(JsonNode row) {
        return new ExpenseRecord(row.path("id").asLong(), ApiSalesService.text(row, "expenseNumber"),
                ApiSalesService.text(row, "categoryCode"), ApiSalesService.text(row, "categoryName"),
                ApiSalesService.text(row, "label"), ApiSalesService.decimal(row, "amount"),
                ApiSalesService.decimal(row, "vatAmount"), ApiSalesService.decimal(row, "totalAmount"),
                ApiSalesService.date(row, "expenseDate"), ApiSalesService.text(row, "paymentMethod"),
                ApiSalesService.text(row, "cashAccountName"), ApiSalesService.text(row, "employeeName"),
                ApiSalesService.text(row, "supplierName"), ApiSalesService.text(row, "vehiclePlate"),
                ApiSalesService.text(row, "status"), ApiSalesService.text(row, "notes"),
                ApiSalesService.text(row, "creatorName"), ApiSalesService.text(row, "approvedByName"));
    }

    private static EmployeeRecord employee(JsonNode row) {
        String full = (ApiSalesService.text(row, "lastName") + " " + ApiSalesService.text(row, "firstName")).trim();
        if (full.isBlank()) {
            full = ApiSalesService.text(row, "fullName");
        }

        return new EmployeeRecord(row.path("id").asLong(), ApiSalesService.text(row, "employeeNumber"), full,
                ApiSalesService.text(row, "departmentName"), ApiSalesService.text(row, "position"),
                ApiSalesService.text(row, "phone"), ApiSalesService.text(row, "email"),
                ApiSalesService.date(row, "hireDate"), ApiSalesService.text(row, "contractType"),
                ApiSalesService.decimal(row, "baseSalary"), row.path("isActive").asBoolean(true),
                ApiSalesService.text(row, "lastPaidPeriod"));
    }

    private static LeaveRecord leave(JsonNode row) {
        return new LeaveRecord(row.path("id").asLong(), row.path("employeeId").asLong(),
                ApiSalesService.text(row, "employee"), ApiSalesService.text(row, "leaveType"),
                ApiSalesService.date(row, "startDate"), ApiSalesService.date(row, "endDate"),
                row.path("days").asLong(), ApiSalesService.text(row, "status"),
                ApiSalesService.text(row, "reason"), ApiSalesService.text(row, "approvedByName"));
    }

    private static SalaryLine salary(JsonNode row) {
        return new SalaryLine(row.path("id").asLong(), row.path("employeeId").asLong(),
                ApiSalesService.text(row, "employee"), ApiSalesService.text(row, "departmentName"),
                ApiSalesService.text(row, "period"), ApiSalesService.decimal(row, "baseAmount"),
                ApiSalesService.decimal(row, "bonus"), ApiSalesService.decimal(row, "deductions"),
                ApiSalesService.decimal(row, "advance"), ApiSalesService.decimal(row, "netAmount"),
                row.path("isPaid").asBoolean(row.hasNonNull("paidAt")),
                ApiSalesService.text(row, "expenseNumber"));
    }

    private static MarginReport margin(JsonNode node) {
        List<MarginReport.Line> lines = new ArrayList<>();
        if (node != null && node.path("lines").isArray()) {
            for (JsonNode row : node.path("lines")) {
                lines.add(new MarginReport.Line(row.path("productId").asLong(), ApiSalesService.text(row, "sku"),
                        ApiSalesService.text(row, "nameFr"), ApiSalesService.text(row, "type"),
                        ApiSalesService.text(row, "categoryName"), ApiSalesService.decimal(row, "quantity"),
                        ApiSalesService.decimal(row, "revenue"), ApiSalesService.decimal(row, "cost"),
                        ApiSalesService.decimal(row, "margin"), ApiSalesService.decimal(row, "marginPct")));
            }
        }
        List<MarginReport.CategoryRow> categories = new ArrayList<>();
        if (node != null && node.path("byCategory").isArray()) {
            for (JsonNode row : node.path("byCategory")) {
                categories.add(new MarginReport.CategoryRow(ApiSalesService.text(row, "categoryName"),
                        ApiSalesService.decimal(row, "revenue"), ApiSalesService.decimal(row, "cost"),
                        ApiSalesService.decimal(row, "margin")));
            }
        }
        JsonNode totals = node == null ? null : node.path("totals");

        return new MarginReport(ApiSalesService.date(node, "from"), ApiSalesService.date(node, "to"), lines,
                categories, ApiSalesService.decimal(totals, "revenue"), ApiSalesService.decimal(totals, "cost"),
                ApiSalesService.decimal(totals, "margin"), ApiSalesService.decimal(totals, "marginPct"));
    }

    private static CashflowReport cashflow(JsonNode node) {
        List<CashflowReport.Month> timeline = new ArrayList<>();
        if (node != null && node.path("timeline").isArray()) {
            for (JsonNode row : node.path("timeline")) {
                timeline.add(new CashflowReport.Month(ApiSalesService.text(row, "period"),
                        ApiSalesService.decimal(row, "totalIn"), ApiSalesService.decimal(row, "totalOut"),
                        ApiSalesService.decimal(row, "net")));
            }
        }
        List<CashflowReport.Source> sources = new ArrayList<>();
        if (node != null && node.path("bySource").isArray()) {
            for (JsonNode row : node.path("bySource")) {
                sources.add(new CashflowReport.Source(ApiSalesService.text(row, "sourceType"),
                        ApiSalesService.decimal(row, "totalIn"), ApiSalesService.decimal(row, "totalOut"),
                        ApiSalesService.decimal(row, "net")));
            }
        }
        JsonNode totals = node == null ? null : node.path("totals");

        return new CashflowReport(ApiSalesService.date(node, "from"), ApiSalesService.date(node, "to"), timeline,
                sources, ApiSalesService.decimal(totals, "totalIn"), ApiSalesService.decimal(totals, "totalOut"),
                ApiSalesService.decimal(totals, "net"));
    }

    private static IncomeStatement statement(JsonNode node) {
        JsonNode income = node == null ? null : node.path("income");
        JsonNode expenses = node == null ? null : node.path("expenses");
        List<IncomeStatement.CategoryExpense> categories = new ArrayList<>();
        if (expenses != null && expenses.path("byCategory").isArray()) {
            for (JsonNode row : expenses.path("byCategory")) {
                categories.add(new IncomeStatement.CategoryExpense(ApiSalesService.text(row, "code"),
                        ApiSalesService.text(row, "nameFr"), ApiSalesService.decimal(row, "amount")));
            }
        }

        return new IncomeStatement(ApiSalesService.text(node, "period"),
                ApiSalesService.decimal(income, "invoicedHt"), ApiSalesService.decimal(income, "creditNotesHt"),
                ApiSalesService.decimal(income, "revenue"), ApiSalesService.decimal(income, "vatCollected"),
                ApiSalesService.decimal(node, "costOfGoods"), ApiSalesService.decimal(node, "grossMargin"),
                ApiSalesService.decimal(node, "grossMarginPct"), ApiSalesService.decimal(expenses, "total"),
                ApiSalesService.decimal(node, "result"), ApiSalesService.decimal(node, "resultPct"), categories);
    }

    private static LocalDateTime dateTime(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.path(field);
        if (value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        String raw = value.asText().replace(' ', 'T');
        try {
            return LocalDateTime.parse(raw.length() > 19 ? raw.substring(0, 19) : raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
