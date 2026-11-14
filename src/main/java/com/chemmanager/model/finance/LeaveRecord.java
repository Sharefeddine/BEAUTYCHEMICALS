package com.chemmanager.model.finance;

import java.time.LocalDate;

/** A leave request, from the request to the decision; only a pending one can be decided. */
public record LeaveRecord(long id, long employeeId, String employee, String leaveType, LocalDate startDate,
                          LocalDate endDate, long days, String status, String reason, String approvedByName) {

    public boolean isPending() {
        return "REQUESTED".equals(status);
    }
}
