package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A staff record: payroll master data, independent from the login account of the person. */
public record EmployeeRecord(long id, String number, String fullName, String departmentName, String position,
                             String phone, String email, LocalDate hireDate, String contractType,
                             BigDecimal baseSalary, boolean active, String lastPaidPeriod) {
}
