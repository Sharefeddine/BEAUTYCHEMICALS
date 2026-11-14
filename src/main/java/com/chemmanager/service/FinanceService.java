package com.chemmanager.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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

/**
 * The money side of the workshop: the accounting calendar, the cash book, the expenses, the staff and
 * their payroll, and the three statements.
 *
 * <p>Every write goes through the server, which is where the rules live: a closed month refuses a
 * posting, a paid expense cannot be cancelled, and paying a salary books an expense of the month.</p>
 */
public interface FinanceService {

    // ------------------------------------------------------------------ treasury and calendar
    Treasury treasury();

    List<AccountingPeriod> periods();

    AccountingPeriod closePeriod(long id);

    AccountingPeriod reopenPeriod(long id);

    PagedResult<CashTransaction> cashbook(CashQuery query);

    CashTransaction postCashMovement(NewCashMovement movement);

    // ------------------------------------------------------------------ expenses
    List<ExpenseCategory> expenseCategories();

    PagedResult<ExpenseRecord> expenses(ExpenseQuery query);

    ExpenseRecord createExpense(NewExpense expense);

    ExpenseRecord approveExpense(long id);

    ExpenseRecord payExpense(long id, long cashAccountId);

    ExpenseRecord cancelExpense(long id);

    ExpenseSummary expenseSummary(LocalDate from, LocalDate to);

    // ------------------------------------------------------------------ staff and payroll
    PagedResult<EmployeeRecord> employees(EmployeeQuery query);

    EmployeeRecord createEmployee(NewEmployee employee);

    EmployeeRecord deactivateEmployee(long id);

    List<AttendanceRecord> attendance(long employeeId, LocalDate from, LocalDate to);

    AttendanceRecord recordAttendance(long employeeId, LocalDate workDate, String status, String checkIn,
                                      String checkOut);

    PagedResult<LeaveRecord> leaveRequests(String status);

    LeaveRecord requestLeave(long employeeId, String leaveType, LocalDate startDate, LocalDate endDate,
                             String reason);

    LeaveRecord decideLeave(long id, Decision decision);

    PagedResult<SalaryLine> salaryPayments(String period);

    int runPayroll(String period);

    SalaryLine paySalary(long id, long cashAccountId);

    // ------------------------------------------------------------------ statements
    MarginReport margin(LocalDate from, LocalDate to);

    CashflowReport cashflow(LocalDate from, LocalDate to);

    IncomeStatement result(String period);

    /** What the expenses of a window cost, per category and in total. */
    record ExpenseSummary(BigDecimal amount, List<IncomeStatement.CategoryExpense> byCategory) {
    }

    /** A manual movement of the cash book, when the money did not come from a document. */
    record NewCashMovement(long cashAccountId, String direction, BigDecimal amount, String description,
                           LocalDate occurredAt) {
    }

    /** An expense as the entry dialog fills it. */
    record NewExpense(long categoryId, String label, BigDecimal amount, BigDecimal vatAmount,
                      LocalDate expenseDate, String paymentMethod, long cashAccountId, String notes) {
    }

    /** A new staff record. */
    record NewEmployee(String firstName, String lastName, long departmentId, String position, String phone,
                       LocalDate hireDate, String contractType, BigDecimal baseSalary) {
    }

    /** APPROVE, REJECT or CANCEL a leave request. */
    enum Decision {
        APPROVE, REJECT, CANCEL
    }
}
