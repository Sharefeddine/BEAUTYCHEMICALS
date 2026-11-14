package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One line of the cash book: money in or out of an account, always traceable to a source. */
public record CashTransaction(long id, long cashAccountId, String accountCode, String accountName,
                              String direction, BigDecimal amount, BigDecimal signedAmount, String sourceType,
                              String description, LocalDateTime occurredAt, String periodCode, String userName) {

    public boolean isInflow() {
        return "IN".equals(direction);
    }
}
