package com.chemmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

import com.chemmanager.dto.CashQuery;
import com.chemmanager.dto.EmployeeQuery;
import com.chemmanager.dto.ExpenseQuery;
import com.chemmanager.model.finance.AccountingPeriod;
import com.chemmanager.model.finance.AttendanceRecord;
import com.chemmanager.model.finance.CashflowReport;
import com.chemmanager.model.finance.EmployeeRecord;
import com.chemmanager.model.finance.ExpenseRecord;
import com.chemmanager.model.finance.IncomeStatement;
import com.chemmanager.model.finance.MarginReport;
import com.chemmanager.model.finance.SalaryLine;
import com.chemmanager.service.mock.MockFinanceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The rules of the finance module, replayed against the offline service: a closed month refuses a
 * posting, the payroll is idempotent, and paying a salary books the expense and moves the cash.
 */
class FinanceServiceMockTest {

    private MockFinanceService finance;

    @BeforeEach
    void setUp() {
        finance = new MockFinanceService();
    }

    @Test
    @DisplayName("the treasury is opening plus what came in minus what went out")
    void treasuryAddsUp() {
        var treasury = finance.treasury();
        assertEquals(2, treasury.accounts().size());
        BigDecimal expected = BigDecimal.ZERO;
        for (var account : treasury.accounts()) {
            assertEquals(0, account.openingBalance().add(account.totalIn()).subtract(account.totalOut())
                    .compareTo(account.balance()), "account " + account.code());
            expected = expected.add(account.balance());
        }
        assertEquals(0, expected.compareTo(treasury.balance()));
        assertTrue(treasury.balance().signum() > 0);
    }

    @Test
    @DisplayName("a closed month refuses a cash movement and an expense, an open one accepts both")
    void closedMonthRefusesPostings() {
        AccountingPeriod closed = finance.periods().stream().filter(AccountingPeriod::isClosed).findFirst()
                .orElseThrow();
        LocalDate closedDay = closed.startDate().plusDays(3);

        assertThrows(IllegalStateException.class, () -> finance.postCashMovement(
                new FinanceService.NewCashMovement(1, "IN", new BigDecimal("1000"), "Hors délai", closedDay)));
        assertThrows(IllegalStateException.class, () -> finance.createExpense(new FinanceService.NewExpense(4,
                "Carburant hors délai", new BigDecimal("2000"), BigDecimal.ZERO, closedDay, "CASH", 0, "")));

        LocalDate today = LocalDate.now();
        var posted = finance.postCashMovement(new FinanceService.NewCashMovement(1, "OUT",
                new BigDecimal("1500"), "Achat fournitures", today));
        assertEquals("OUT", posted.direction());
        assertEquals(0, posted.signedAmount().compareTo(new BigDecimal("-1500")));
    }

    @Test
    @DisplayName("an expense walks from draft to paid, and a paid one is not cancelled")
    void expenseLifecycle() {
        var created = finance.createExpense(new FinanceService.NewExpense(4, "Vidange camion",
                new BigDecimal("10000"), new BigDecimal("1900"), LocalDate.now(), "CASH", 1, ""));
        assertTrue(created.isDraft());
        assertEquals(0, created.totalAmount().compareTo(new BigDecimal("11900")));

        var approved = finance.approveExpense(created.id());
        assertEquals("APPROVED", approved.status());
        assertThrows(IllegalStateException.class, () -> finance.approveExpense(created.id()));

        BigDecimal before = finance.treasury().balance();
        var paid = finance.payExpense(created.id(), 1);
        assertEquals("PAID", paid.status());
        assertThrows(IllegalStateException.class, () -> finance.cancelExpense(created.id()));
        assertThrows(IllegalStateException.class, () -> finance.payExpense(created.id(), 1));
        assertEquals(0, before.subtract(new BigDecimal("11900")).compareTo(finance.treasury().balance()));
    }

    @Test
    @DisplayName("the payroll runs once per month and paying a line books the salary expense")
    void payrollRunAndPayment() {
        String period = YearMonth.now().toString();
        int created = finance.runPayroll(period);
        assertEquals(3, created, "one line per active employee");
        assertEquals(0, finance.runPayroll(period), "the second run creates nothing");

        List<SalaryLine> lines = finance.salaryPayments(period).items();
        assertTrue(lines.stream().noneMatch(SalaryLine::paid));
        SalaryLine line = lines.stream().min(Comparator.comparing(SalaryLine::netAmount)).orElseThrow();
        BigDecimal before = finance.treasury().balance();

        SalaryLine paid = finance.paySalary(line.id(), 1);
        assertTrue(paid.paid());
        assertFalse(paid.expenseNumber() == null || paid.expenseNumber().isBlank());
        assertEquals(0, before.subtract(line.netAmount()).compareTo(finance.treasury().balance()));
        assertThrows(IllegalStateException.class, () -> finance.paySalary(line.id(), 1));

        List<ExpenseRecord> salaryExpenses = finance.expenses(ExpenseQuery.initial(50)).items().stream()
                .filter(row -> "SALARIES".equals(row.categoryCode())).toList();
        assertTrue(salaryExpenses.stream().anyMatch(ExpenseRecord::isPaid));
    }

    @Test
    @DisplayName("a closed month refuses the payroll run")
    void payrollRefusesClosedMonth() {
        String closed = finance.periods().stream().filter(AccountingPeriod::isClosed)
                .map(AccountingPeriod::code).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> finance.runPayroll(closed));
    }

    @Test
    @DisplayName("the attendance sheet keeps one row per employee and day")
    void attendanceIsAnUpsert() {
        EmployeeRecord employee = finance.employees(EmployeeQuery.initial(10)).items().get(0);
        LocalDate day = LocalDate.now().minusDays(2);
        AttendanceRecord first = finance.recordAttendance(employee.id(), day, "PRESENT", "07:45", "17:10");
        AttendanceRecord second = finance.recordAttendance(employee.id(), day, "ABSENT", null, null);
        assertEquals(first.id(), second.id());
        long sameDay = finance.attendance(employee.id(), day, day).stream()
                .filter(row -> row.workDate().equals(day)).count();
        assertEquals(1, sameDay);
    }

    @Test
    @DisplayName("a leave request is decided once, and only while pending")
    void leaveDecisions() {
        EmployeeRecord employee = finance.employees(EmployeeQuery.initial(10)).items().get(0);
        var leave = finance.requestLeave(employee.id(), "ANNUAL", LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(14), "Repos");
        assertEquals("REQUESTED", leave.status());
        assertEquals(5, leave.days());

        var approved = finance.decideLeave(leave.id(), FinanceService.Decision.APPROVE);
        assertEquals("APPROVED", approved.status());
        assertThrows(IllegalStateException.class,
                () -> finance.decideLeave(leave.id(), FinanceService.Decision.REJECT));
    }

    @Test
    @DisplayName("the statements are internally consistent")
    void statementsAddUp() {
        LocalDate from = LocalDate.now().withDayOfMonth(1);
        MarginReport margin = finance.margin(from, LocalDate.now());
        assertEquals(0, margin.revenue().subtract(margin.cost()).compareTo(margin.margin()));
        assertFalse(margin.lines().isEmpty());
        for (MarginReport.Line line : margin.lines()) {
            assertEquals(0, line.revenue().subtract(line.cost()).compareTo(line.margin()));
        }

        CashflowReport cashflow = finance.cashflow(from, LocalDate.now());
        assertEquals(0, cashflow.totalIn().subtract(cashflow.totalOut()).compareTo(cashflow.net()));

        IncomeStatement statement = finance.result(YearMonth.now().toString());
        assertEquals(0, statement.revenue().subtract(statement.costOfGoods())
                .compareTo(statement.grossMargin()));
        assertEquals(0, statement.grossMargin().subtract(statement.expenses())
                .compareTo(statement.result()));
        assertFalse(statement.byCategory().isEmpty());
    }

    @Test
    @DisplayName("the cash book filters and pages")
    void cashBookFilters() {
        var outOnly = finance.cashbook(CashQuery.initial(50).withDirection("OUT")).items();
        assertFalse(outOnly.isEmpty());
        assertTrue(outOnly.stream().allMatch(row -> "OUT".equals(row.direction())));

        var firstPage = finance.cashbook(CashQuery.initial(2));
        assertEquals(2, firstPage.items().size());
        assertTrue(firstPage.total() > 2);
    }
}
