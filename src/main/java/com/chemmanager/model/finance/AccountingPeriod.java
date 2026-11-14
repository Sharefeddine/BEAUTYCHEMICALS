package com.chemmanager.model.finance;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A month of the accounting calendar: open months accept postings, closed months refuse them. */
public record AccountingPeriod(long id, String code, LocalDate startDate, LocalDate endDate, String status,
                               String closedByName, LocalDateTime closedAt, int transactionCount) {

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }
}
