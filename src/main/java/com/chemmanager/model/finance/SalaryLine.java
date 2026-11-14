package com.chemmanager.model.finance;

import java.math.BigDecimal;

/** A payroll line of a month; paying it books the expense and moves the cash account. */
public record SalaryLine(long id, long employeeId, String employee, String departmentName, String period,
                         BigDecimal baseAmount, BigDecimal bonus, BigDecimal deductions, BigDecimal advance,
                         BigDecimal netAmount, boolean paid, String expenseNumber) {
}
