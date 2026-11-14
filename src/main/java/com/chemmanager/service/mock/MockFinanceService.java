package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

/**
 * Offline demo of the finance module. The rules are replayed in memory with the same shape as the
 * server: a closed month refuses a posting, a paid expense cannot be cancelled, and paying a salary
 * books an expense of the month.
 */
public final class MockFinanceService implements FinanceService {

    private static final BigDecimal VAT = new BigDecimal("19");

    private final List<Treasury.Account> accounts = new ArrayList<>();
    private final List<AccountingPeriod> periods = new ArrayList<>();
    private final List<CashTransaction> transactions = new ArrayList<>();
    private final List<ExpenseCategory> categories = new ArrayList<>();
    private final List<ExpenseRecord> expenses = new ArrayList<>();
    private final List<EmployeeRecord> employees = new ArrayList<>();
    private final List<AttendanceRecord> attendance = new ArrayList<>();
    private final List<LeaveRecord> leaves = new ArrayList<>();
    private final List<SalaryLine> salaries = new ArrayList<>();

    private long nextTransactionId = 100;
    private long nextExpenseId = 100;
    private long nextEmployeeId = 100;
    private long nextLeaveId = 100;
    private long nextSalaryId = 100;

    public MockFinanceService() {
        YearMonth current = YearMonth.now();
        accounts.add(new Treasury.Account(1, "CAISSE", "Caisse principale", "CASH",
                new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        accounts.add(new Treasury.Account(2, "BANQUE", "Compte bancaire BNA", "BANK",
                new BigDecimal("300000.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        for (int offset = -3; offset <= 1; offset++) {
            YearMonth month = current.plusMonths(offset);
            boolean open = offset >= 0;
            periods.add(new AccountingPeriod(10 + offset + 3, month.toString(), month.atDay(1), month.atEndOfMonth(),
                    open ? "OPEN" : "CLOSED", open ? null : "Nadia Belkacem", open ? null : month.atDay(2).atTime(9, 0),
                    open ? 4 + offset + 3 : 9 + offset + 3));
        }

        transactions.add(transaction(1, "IN", "PAYMENT", "Encaissement client - Pharmarcie El Amel",
                new BigDecimal("180000.00"), current.atDay(3).atTime(10, 15)));
        transactions.add(transaction(1, "OUT", "EXPENSE", "Carburant - tournée Sétif", new BigDecimal("9500.00"),
                current.atDay(6).atTime(8, 40)));
        transactions.add(transaction(2, "IN", "PAYMENT", "Virement reçu - Grossiste Oran",
                new BigDecimal("250000.00"), current.atDay(8).atTime(11, 5)));
        transactions.add(transaction(2, "OUT", "EXPENSE", "Loyer du dépôt", new BigDecimal("45000.00"),
                current.minusMonths(1).atDay(5).atTime(9, 30)));
        transactions.add(transaction(1, "OUT", "EXPENSE", "Électricité atelier", new BigDecimal("12400.00"),
                current.minusMonths(1).atDay(12).atTime(14, 0)));

        categories.add(new ExpenseCategory(1, "RENT", "Loyers", "الإيجار", false, false, true));
        categories.add(new ExpenseCategory(2, "SALARIES", "Salaires", "الرواتب", true, false, true));
        categories.add(new ExpenseCategory(3, "ENERGY", "Énergie", "الطاقة", false, false, true));
        categories.add(new ExpenseCategory(4, "FUEL", "Carburant", "الوقود", false, true, true));
        categories.add(new ExpenseCategory(5, "MAINTENANCE", "Maintenance", "الصيانة", false, false, true));

        expenses.add(expense(1, "RENT", "RENT", "Loyer du dépôt - " + current, new BigDecimal("45000.00"),
                current.atDay(5), "TRANSFER", "PAID", "Nadia Belkacem"));
        expenses.add(expense(2, "FUEL", "FUEL", "Carburant camion 19-2145-116", new BigDecimal("9500.00"),
                current.atDay(6), "CASH", "PAID", "Amine Boudjelal"));
        expenses.add(expense(3, "ENERGY", "ENERGY", "Électricité atelier", new BigDecimal("12400.00"),
                current.atDay(9), "CASH", "APPROVED", null));
        expenses.add(expense(4, "MAINTENANCE", "MAINTENANCE", "Révision du groupe froid", new BigDecimal("32000.00"),
                current.atDay(11), "CASH", "DRAFT", null));

        employees.add(new EmployeeRecord(1, "EMP-0001", "Boudjelal Amine", "Distribution", "Chauffeur livreur",
                "0550 12 34 56", "amine@chempro.dz", LocalDate.of(2024, 3, 1), "CDI", new BigDecimal("45000.00"),
                true, null));
        employees.add(new EmployeeRecord(2, "EMP-0002", "Belkacem Nadia", "Finance", "Comptable",
                "0661 98 76 54", "nadia@chempro.dz", LocalDate.of(2022, 9, 15), "CDI", new BigDecimal("62000.00"),
                true, null));
        employees.add(new EmployeeRecord(3, "EMP-0003", "Meziane Sofiane", "Production", "Opérateur de mélange",
                "0770 44 55 66", "sofiane@chempro.dz", LocalDate.of(2025, 1, 6), "CDD", new BigDecimal("38000.00"),
                true, null));

        leaves.add(new LeaveRecord(1, 2, "EMP-0002 - Belkacem Nadia", "ANNUAL", current.atDay(20),
                current.plusMonths(1).atDay(2), 14, "APPROVED", "Congés annuels", "Direction"));
        leaves.add(new LeaveRecord(2, 1, "EMP-0001 - Boudjelal Amine", "SICK", current.atDay(12), current.atDay(14),
                3, "REQUESTED", "Angine", null));

        nextTransactionId = 200;
        nextExpenseId = 200;
        nextEmployeeId = 200;
        nextLeaveId = 200;
        nextSalaryId = 200;
    }

    // ------------------------------------------------------------------ treasury and calendar

    @Override
    public Treasury treasury() {
        List<Treasury.Account> computed = new ArrayList<>();
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal balance = BigDecimal.ZERO;
        for (Treasury.Account account : accounts) {
            BigDecimal in = sum(transactions, account.id(), "IN");
            BigDecimal out = sum(transactions, account.id(), "OUT");
            BigDecimal value = account.openingBalance().add(in).subtract(out);
            totalIn = totalIn.add(in);
            totalOut = totalOut.add(out);
            balance = balance.add(value);
            computed.add(new Treasury.Account(account.id(), account.code(), account.name(), account.type(),
                    account.openingBalance(), in, out, value));
        }

        return new Treasury(computed, balance, totalIn, totalOut, new BigDecimal("184000.00"),
                new BigDecimal("96000.00"));
    }

    @Override
    public List<AccountingPeriod> periods() {
        return List.copyOf(periods);
    }

    @Override
    public AccountingPeriod closePeriod(long id) {
        AccountingPeriod period = period(id);
        if (!period.isOpen()) {
            throw new IllegalStateException("This accounting period is already closed.");
        }
        AccountingPeriod closed = new AccountingPeriod(period.id(), period.code(), period.startDate(),
                period.endDate(), "CLOSED", "Nadia Belkacem", LocalDateTime.now(), period.transactionCount());
        replacePeriod(closed);

        return closed;
    }

    @Override
    public AccountingPeriod reopenPeriod(long id) {
        AccountingPeriod period = period(id);
        if (period.isOpen()) {
            throw new IllegalStateException("This accounting period is already open.");
        }
        AccountingPeriod open = new AccountingPeriod(period.id(), period.code(), period.startDate(),
                period.endDate(), "OPEN", null, null, period.transactionCount());
        replacePeriod(open);

        return open;
    }

    @Override
    public PagedResult<CashTransaction> cashbook(CashQuery query) {
        CashQuery effective = query == null ? CashQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().toLowerCase();
        List<CashTransaction> rows = new ArrayList<>();
        for (CashTransaction row : transactions) {
            if (!search.isBlank() && !(row.description().toLowerCase().contains(search)
                    || row.accountName().toLowerCase().contains(search)
                    || row.sourceType().toLowerCase().contains(search))) {
                continue;
            }
            if (!effective.direction().isBlank() && !effective.direction().equals(row.direction())) {
                continue;
            }
            if (effective.cashAccountId() > 0 && row.cashAccountId() != effective.cashAccountId()) {
                continue;
            }
            if (effective.from() != null && row.occurredAt().toLocalDate().isBefore(effective.from())) {
                continue;
            }
            if (effective.to() != null && row.occurredAt().toLocalDate().isAfter(effective.to())) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(CashTransaction::occurredAt).reversed());

        return page(rows, effective.page(), effective.pageSize());
    }

    @Override
    public CashTransaction postCashMovement(NewCashMovement movement) {
        Treasury.Account account = account(movement.cashAccountId());
        String periodCode = YearMonth.from(movement.occurredAt() == null ? LocalDate.now() : movement.occurredAt())
                .toString();
        if (period(periodCode).isClosed()) {
            throw new IllegalStateException("The period " + periodCode + " is closed.");
        }
        CashTransaction row = transaction(account.id(), movement.direction(), "OTHER",
                movement.description() == null ? "" : movement.description(), movement.amount(),
                (movement.occurredAt() == null ? LocalDate.now() : movement.occurredAt()).atTime(12, 0));
        transactions.add(row);

        return row;
    }

    // ------------------------------------------------------------------ expenses

    @Override
    public List<ExpenseCategory> expenseCategories() {
        return List.copyOf(categories);
    }

    @Override
    public PagedResult<ExpenseRecord> expenses(ExpenseQuery query) {
        ExpenseQuery effective = query == null ? ExpenseQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().toLowerCase();
        List<ExpenseRecord> rows = new ArrayList<>();
        for (ExpenseRecord row : expenses) {
            if (!search.isBlank() && !(row.label().toLowerCase().contains(search)
                    || row.number().toLowerCase().contains(search)
                    || row.categoryName().toLowerCase().contains(search))) {
                continue;
            }
            if (effective.categoryId() > 0 && !row.categoryCode().equals(codeOf(effective.categoryId()))) {
                continue;
            }
            if (!effective.status().isBlank() && !effective.status().equals(row.status())) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(ExpenseRecord::expenseDate).reversed());

        return page(rows, effective.page(), effective.pageSize());
    }

    @Override
    public ExpenseRecord createExpense(NewExpense request) {
        ExpenseCategory category = categories.stream().filter(c -> c.id() == request.categoryId()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown expense category."));
        if (request.label() == null || request.label().isBlank()) {
            throw new IllegalStateException("The label is required.");
        }
        BigDecimal amount = request.amount() == null ? BigDecimal.ZERO : request.amount();
        if (amount.signum() <= 0) {
            throw new IllegalStateException("The amount must be positive.");
        }
        LocalDate date = request.expenseDate() == null ? LocalDate.now() : request.expenseDate();
        if (period(YearMonth.from(date).toString()).isClosed()) {
            throw new IllegalStateException("The period is closed: this expense cannot be entered.");
        }
        ExpenseRecord row = new ExpenseRecord(nextExpenseId++, String.format("DP-%05d", nextExpenseId),
                category.code(), category.nameFr(), request.label(), amount,
                request.vatAmount() == null ? BigDecimal.ZERO : request.vatAmount(),
                amount.add(request.vatAmount() == null ? BigDecimal.ZERO : request.vatAmount()), date,
                request.paymentMethod() == null ? "CASH" : request.paymentMethod(),
                request.cashAccountId() > 0 ? account(request.cashAccountId()).name() : "", "", "", "",
                "DRAFT", request.notes() == null ? "" : request.notes(), "Vous", null);
        expenses.add(row);

        return row;
    }

    @Override
    public ExpenseRecord approveExpense(long id) {
        ExpenseRecord row = expense(id);
        if (!row.canBeApproved()) {
            throw new IllegalStateException("Only a draft expense can be approved.");
        }

        return replaceExpense(withStatus(row, "APPROVED", "Vous"));
    }

    @Override
    public ExpenseRecord payExpense(long id, long cashAccountId) {
        ExpenseRecord row = expense(id);
        if (!row.canBePaid()) {
            throw new IllegalStateException("This expense cannot be paid in its current state.");
        }
        Treasury.Account account = account(cashAccountId);
        if (period(YearMonth.from(row.expenseDate()).toString()).isClosed()) {
            throw new IllegalStateException("The period of this expense is closed: it cannot be paid.");
        }
        transactions.add(transaction(account.id(), "OUT", "EXPENSE", row.label(), row.totalAmount(),
                row.expenseDate().atTime(12, 0)));

        return replaceExpense(withStatus(row, "PAID", row.approvedByName() == null ? "Vous" : row.approvedByName()));
    }

    @Override
    public ExpenseRecord cancelExpense(long id) {
        ExpenseRecord row = expense(id);
        if (!row.canBeCancelled()) {
            throw new IllegalStateException("A paid expense is not cancelled.");
        }

        return replaceExpense(withStatus(row, "CANCELLED", row.approvedByName()));
    }

    @Override
    public ExpenseSummary expenseSummary(LocalDate from, LocalDate to) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseRecord row : expenses) {
            if (row.isCancelled()) {
                continue;
            }
            if (from != null && row.expenseDate().isBefore(from)) {
                continue;
            }
            if (to != null && row.expenseDate().isAfter(to)) {
                continue;
            }
            totals.merge(row.categoryCode(), row.amount(), BigDecimal::add);
            total = total.add(row.amount());
        }
        List<IncomeStatement.CategoryExpense> rows = new ArrayList<>();
        totals.forEach((code, amount) -> rows.add(new IncomeStatement.CategoryExpense(code,
                categories.stream().filter(c -> c.code().equals(code)).map(ExpenseCategory::nameFr).findFirst()
                        .orElse(code), amount)));
        rows.sort(Comparator.comparing(IncomeStatement.CategoryExpense::amount).reversed());

        return new ExpenseSummary(total, rows);
    }

    // ------------------------------------------------------------------ staff and payroll

    @Override
    public PagedResult<EmployeeRecord> employees(EmployeeQuery query) {
        EmployeeQuery effective = query == null ? EmployeeQuery.initial(25) : query;
        String search = effective.search() == null ? "" : effective.search().toLowerCase();
        List<EmployeeRecord> rows = new ArrayList<>();
        for (EmployeeRecord row : employees) {
            if (effective.activeOnly() != null && row.active() != effective.activeOnly()) {
                continue;
            }
            if (!search.isBlank() && !(row.fullName().toLowerCase().contains(search)
                    || row.number().toLowerCase().contains(search)
                    || row.position().toLowerCase().contains(search))) {
                continue;
            }
            rows.add(row);
        }

        return page(rows, effective.page(), effective.pageSize());
    }

    @Override
    public EmployeeRecord createEmployee(NewEmployee request) {
        if (request.firstName() == null || request.firstName().isBlank()
                || request.lastName() == null || request.lastName().isBlank()) {
            throw new IllegalStateException("The first and last names are required.");
        }
        String department = employees.stream().map(EmployeeRecord::departmentName).findFirst().orElse("Production");
        EmployeeRecord row = new EmployeeRecord(nextEmployeeId, String.format("EMP-%04d", nextEmployeeId),
                request.lastName() + " " + request.firstName(), department, request.position(),
                request.phone(), "", request.hireDate(), request.contractType(),
                request.baseSalary() == null ? BigDecimal.ZERO : request.baseSalary(), true, null);
        nextEmployeeId++;
        employees.add(row);

        return row;
    }

    @Override
    public EmployeeRecord deactivateEmployee(long id) {
        EmployeeRecord row = employee(id);
        EmployeeRecord inactive = new EmployeeRecord(row.id(), row.number(), row.fullName(), row.departmentName(),
                row.position(), row.phone(), row.email(), row.hireDate(), row.contractType(), row.baseSalary(),
                false, row.lastPaidPeriod());
        employees.remove(row);
        employees.add(inactive);

        return inactive;
    }

    @Override
    public List<AttendanceRecord> attendance(long employeeId, LocalDate from, LocalDate to) {
        List<AttendanceRecord> rows = new ArrayList<>();
        for (AttendanceRecord row : attendance) {
            if (from != null && row.workDate().isBefore(from)) {
                continue;
            }
            if (to != null && row.workDate().isAfter(to)) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(AttendanceRecord::workDate).reversed());

        return rows;
    }

    @Override
    public AttendanceRecord recordAttendance(long employeeId, LocalDate workDate, String status, String checkIn,
                                             String checkOut) {
        employee(employeeId);
        LocalDate date = workDate == null ? LocalDate.now() : workDate;
        AttendanceRecord existing = attendance.stream().filter(row -> row.workDate().equals(date)).findFirst()
                .orElse(null);
        AttendanceRecord row = new AttendanceRecord(existing == null ? attendance.size() + 1L : existing.id(), date,
                status, checkIn == null ? "" : checkIn, checkOut == null ? "" : checkOut, "");
        attendance.removeIf(candidate -> candidate.workDate().equals(date));
        attendance.add(row);

        return row;
    }

    @Override
    public PagedResult<LeaveRecord> leaveRequests(String status) {
        List<LeaveRecord> rows = new ArrayList<>();
        for (LeaveRecord row : leaves) {
            if (status != null && !status.isBlank() && !status.equals(row.status())) {
                continue;
            }
            rows.add(row);
        }

        return page(rows, 0, 50);
    }

    @Override
    public LeaveRecord requestLeave(long employeeId, String leaveType, LocalDate startDate, LocalDate endDate,
                                    String reason) {
        EmployeeRecord employee = employee(employeeId);
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalStateException("The end of the leave must follow its start.");
        }
        LeaveRecord row = new LeaveRecord(nextLeaveId++, employeeId, employee.number() + " - " + employee.fullName(),
                leaveType == null ? "ANNUAL" : leaveType, startDate, endDate,
                startDate.until(endDate).getDays() + 1L, "REQUESTED", reason == null ? "" : reason, null);
        leaves.add(row);

        return row;
    }

    @Override
    public LeaveRecord decideLeave(long id, Decision decision) {
        LeaveRecord row = leaves.stream().filter(leave -> leave.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown leave request."));
        if (!row.isPending()) {
            throw new IllegalStateException("Only a pending request can be decided.");
        }
        String status = decision == Decision.APPROVE ? "APPROVED"
                : decision == Decision.REJECT ? "REJECTED" : "CANCELLED";
        LeaveRecord decided = new LeaveRecord(row.id(), row.employeeId(), row.employee(), row.leaveType(),
                row.startDate(), row.endDate(), row.days(), status, row.reason(),
                decision == Decision.APPROVE ? "Vous" : null);
        leaves.remove(row);
        leaves.add(decided);

        return decided;
    }

    @Override
    public PagedResult<SalaryLine> salaryPayments(String period) {
        List<SalaryLine> rows = new ArrayList<>();
        for (SalaryLine row : salaries) {
            if (period != null && !period.isBlank() && !period.equals(row.period())) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparing(SalaryLine::period).reversed());

        return page(rows, 0, 200);
    }

    @Override
    public int runPayroll(String period) {
        String code = period == null || period.isBlank() ? YearMonth.now().toString() : period;
        if (period(code).isClosed()) {
            throw new IllegalStateException("The period " + code + " is closed.");
        }
        int created = 0;
        for (EmployeeRecord employee : employees) {
            if (!employee.active()) {
                continue;
            }
            boolean exists = salaries.stream()
                    .anyMatch(row -> row.employeeId() == employee.id() && row.period().equals(code));
            if (exists) {
                continue;
            }
            salaries.add(new SalaryLine(nextSalaryId++, employee.id(), employee.fullName(),
                    employee.departmentName(), code, employee.baseSalary(), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, employee.baseSalary(), false, null));
            created++;
        }

        return created;
    }

    @Override
    public SalaryLine paySalary(long id, long cashAccountId) {
        SalaryLine row = salaries.stream().filter(line -> line.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown salary line."));
        if (row.paid()) {
            throw new IllegalStateException("This salary has already been paid.");
        }
        Treasury.Account account = account(cashAccountId);
        if (period(row.period()).isClosed()) {
            throw new IllegalStateException("The period " + row.period() + " is closed.");
        }
        ExpenseRecord booked = createExpense(new NewExpense(salaryCategoryId(),
                "Salaire " + row.period() + " - " + row.employee(), row.netAmount(), BigDecimal.ZERO,
                YearMonth.parse(row.period()).atDay(28), "TRANSFER", account.id(), "Paie " + row.period()));
        payExpense(booked.id(), account.id());
        SalaryLine paid = new SalaryLine(row.id(), row.employeeId(), row.employee(), row.departmentName(),
                row.period(), row.baseAmount(), row.bonus(), row.deductions(), row.advance(), row.netAmount(), true,
                booked.number());
        salaries.remove(row);
        salaries.add(paid);

        return paid;
    }

    // ------------------------------------------------------------------ statements

    @Override
    public MarginReport margin(LocalDate from, LocalDate to) {
        List<MarginReport.Line> lines = new ArrayList<>();
        lines.add(line(1, "SKU-CL-001", "Eau de Javel 1L", "MANUFACTURED", "Détergents", "4200",
                new BigDecimal("231000.00"), new BigDecimal("138600.00")));
        lines.add(line(2, "SKU-SV-002", "Savon liquide 500ml", "MANUFACTURED", "Hygiène", "3100",
                new BigDecimal("186000.00"), new BigDecimal("117800.00")));
        lines.add(line(3, "SKU-DS-003", "Détergent sol 5L", "TRADED", "Détergents", "640",
                new BigDecimal("83200.00"), new BigDecimal("61440.00")));
        lines.sort(Comparator.comparing(MarginReport.Line::margin).reversed());

        Map<String, MarginReport.CategoryRow> byCategory = new LinkedHashMap<>();
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (MarginReport.Line row : lines) {
            revenue = revenue.add(row.revenue());
            cost = cost.add(row.cost());
            MarginReport.CategoryRow current = byCategory.get(row.categoryName());
            byCategory.put(row.categoryName(), new MarginReport.CategoryRow(row.categoryName(),
                    (current == null ? BigDecimal.ZERO : current.revenue()).add(row.revenue()),
                    (current == null ? BigDecimal.ZERO : current.cost()).add(row.cost()),
                    (current == null ? BigDecimal.ZERO : current.margin()).add(row.margin())));
        }
        BigDecimal margin = revenue.subtract(cost);

        return new MarginReport(from == null ? YearMonth.now().atDay(1) : from,
                to == null ? LocalDate.now() : to, lines, new ArrayList<>(byCategory.values()), revenue, cost,
                margin, pct(margin, revenue));
    }

    @Override
    public CashflowReport cashflow(LocalDate from, LocalDate to) {
        YearMonth current = YearMonth.now();
        List<CashflowReport.Month> timeline = new ArrayList<>();
        timeline.add(new CashflowReport.Month(current.minusMonths(1).toString(), new BigDecimal("250000.00"),
                new BigDecimal("57400.00"), new BigDecimal("192600.00")));
        timeline.add(new CashflowReport.Month(current.toString(), new BigDecimal("180000.00"),
                new BigDecimal("9500.00"), new BigDecimal("170500.00")));
        List<CashflowReport.Source> sources = new ArrayList<>();
        sources.add(new CashflowReport.Source("PAYMENT", new BigDecimal("430000.00"), BigDecimal.ZERO,
                new BigDecimal("430000.00")));
        sources.add(new CashflowReport.Source("EXPENSE", BigDecimal.ZERO, new BigDecimal("66900.00"),
                new BigDecimal("-66900.00")));
        BigDecimal totalIn = new BigDecimal("430000.00");
        BigDecimal totalOut = new BigDecimal("66900.00");

        return new CashflowReport(from == null ? current.minusMonths(1).atDay(1) : from,
                to == null ? LocalDate.now() : to, timeline, sources, totalIn, totalOut,
                totalIn.subtract(totalOut));
    }

    @Override
    public IncomeStatement result(String period) {
        String code = period == null || period.isBlank() ? YearMonth.now().toString() : period;
        BigDecimal invoiced = new BigDecimal("500000.00");
        BigDecimal credits = new BigDecimal("20800.00");
        BigDecimal revenue = invoiced.subtract(credits);
        BigDecimal goods = new BigDecimal("317840.00");
        BigDecimal gross = revenue.subtract(goods);
        BigDecimal operating = new BigDecimal("66900.00");
        List<IncomeStatement.CategoryExpense> categories = List.of(
                new IncomeStatement.CategoryExpense("RENT", "Loyers", new BigDecimal("45000.00")),
                new IncomeStatement.CategoryExpense("ENERGY", "Énergie", new BigDecimal("12400.00")),
                new IncomeStatement.CategoryExpense("FUEL", "Carburant", new BigDecimal("9500.00")));

        return new IncomeStatement(code, invoiced, credits, revenue, new BigDecimal("91000.00"), goods, gross,
                pct(gross, revenue), operating, gross.subtract(operating), pct(gross.subtract(operating), revenue),
                categories);
    }

    // ------------------------------------------------------------------ helpers

    private long salaryCategoryId() {
        return categories.stream().filter(ExpenseCategory::salary).map(ExpenseCategory::id).findFirst().orElse(2L);
    }

    private MarginReport.Line line(long id, String sku, String name, String type, String category, String quantity,
                                   BigDecimal revenue, BigDecimal cost) {
        BigDecimal margin = revenue.subtract(cost);

        return new MarginReport.Line(id, sku, name, type, category, new BigDecimal(quantity), revenue, cost, margin,
                pct(margin, revenue));
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO;
        }

        return part.multiply(new BigDecimal("100")).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<CashTransaction> rows, long accountId, String direction) {
        BigDecimal total = BigDecimal.ZERO;
        for (CashTransaction row : rows) {
            if (row.cashAccountId() == accountId && row.direction().equals(direction)) {
                total = total.add(row.amount());
            }
        }

        return total;
    }

    private CashTransaction transaction(long accountId, String direction, String sourceType, String description,
                                        BigDecimal amount, LocalDateTime occurredAt) {
        Treasury.Account account = account(accountId);
        BigDecimal signed = "IN".equals(direction) ? amount : amount.negate();

        return new CashTransaction(nextTransactionId++, account.id(), account.code(), account.name(), direction,
                amount, signed, sourceType, description, occurredAt, YearMonth.from(occurredAt).toString(), "admin");
    }

    private ExpenseRecord expense(long id, String categoryCode, String categoryName, String label, BigDecimal amount,
                                  LocalDate date, String method, String status, String approver) {
        BigDecimal vat = amount.multiply(VAT).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        return new ExpenseRecord(id, String.format("DP-%05d", id), categoryCode, categoryName, label, amount, vat,
                amount.add(vat), date, method, "Caisse principale", "", "", "", status, "", "Nadia Belkacem",
                approver);
    }

    private ExpenseRecord withStatus(ExpenseRecord row, String status, String approver) {
        return new ExpenseRecord(row.id(), row.number(), row.categoryCode(), row.categoryName(), row.label(),
                row.amount(), row.vatAmount(), row.totalAmount(), row.expenseDate(), row.paymentMethod(),
                row.cashAccountName(), row.employeeName(), row.supplierName(), row.vehiclePlate(), status,
                row.notes(), row.creatorName(), approver);
    }

    private ExpenseRecord replaceExpense(ExpenseRecord row) {
        expenses.removeIf(candidate -> candidate.id() == row.id());
        expenses.add(row);

        return row;
    }

    private ExpenseRecord expense(long id) {
        return expenses.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown expense."));
    }

    private EmployeeRecord employee(long id) {
        return employees.stream().filter(row -> row.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown employee."));
    }

    private String codeOf(long categoryId) {
        return categories.stream().filter(category -> category.id() == categoryId).map(ExpenseCategory::code)
                .findFirst().orElse("");
    }

    private Treasury.Account account(long id) {
        return accounts.stream().filter(account -> account.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown cash account."));
    }

    private AccountingPeriod period(long id) {
        return periods.stream().filter(period -> period.id() == id).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown accounting period."));
    }

    private AccountingPeriod period(String code) {
        return periods.stream().filter(period -> period.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown accounting period " + code + "."));
    }

    private void replacePeriod(AccountingPeriod period) {
        periods.removeIf(candidate -> candidate.id() == period.id());
        periods.add(period);
    }

    private static <T> PagedResult<T> page(List<T> rows, int page, int pageSize) {
        int size = Math.max(1, pageSize);
        int from = Math.min(rows.size(), Math.max(0, page) * size);
        int to = Math.min(rows.size(), from + size);

        return new PagedResult<>(new ArrayList<>(rows.subList(from, to)), Math.max(0, page), size, rows.size());
    }
}
