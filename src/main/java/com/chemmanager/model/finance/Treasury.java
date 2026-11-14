package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.util.List;

/**
 * The treasury as the finance screen reads it: one line per cash account plus the totals the
 * summary endpoint computes, so the screen never adds money up on its own.
 */
public record Treasury(List<Account> accounts, BigDecimal balance, BigDecimal income, BigDecimal expense,
                       BigDecimal receivable, BigDecimal payable) {

    public Treasury {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        balance = balance == null ? BigDecimal.ZERO : balance;
        income = income == null ? BigDecimal.ZERO : income;
        expense = expense == null ? BigDecimal.ZERO : expense;
        receivable = receivable == null ? BigDecimal.ZERO : receivable;
        payable = payable == null ? BigDecimal.ZERO : payable;
    }

    /** One cash account: its opening balance, what moved during its life and where it stands. */
    public record Account(long id, String code, String name, String type, BigDecimal openingBalance,
                          BigDecimal totalIn, BigDecimal totalOut, BigDecimal balance) {

        public Account {
            openingBalance = openingBalance == null ? BigDecimal.ZERO : openingBalance;
            totalIn = totalIn == null ? BigDecimal.ZERO : totalIn;
            totalOut = totalOut == null ? BigDecimal.ZERO : totalOut;
            balance = balance == null ? BigDecimal.ZERO : balance;
        }
    }
}
