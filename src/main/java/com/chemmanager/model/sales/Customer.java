package com.chemmanager.model.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A customer as the server reports it: the file, the credit limit and the receivable it carries.
 *
 * <p>The balance is the money the customer owes; {@code availableCredit} is what is left of the
 * limit. A limit of zero means "cash customer": nothing may be left outstanding.</p>
 */
public record Customer(
        long id,
        String code,
        String name,
        String type,
        String phone,
        String email,
        String address,
        String wilaya,
        BigDecimal creditLimit,
        BigDecimal balance,
        BigDecimal availableCredit,
        String priceSegment,
        String notes,
        boolean active,
        int invoiceCount,
        int orderCount,
        BigDecimal outstanding) {

    public Customer {
        creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        balance = balance == null ? BigDecimal.ZERO : balance;
        outstanding = outstanding == null ? BigDecimal.ZERO : outstanding;
        type = type == null ? "COMPANY" : type;
        priceSegment = priceSegment == null ? "RETAIL" : priceSegment;
    }

    /** True when the customer has a credit line at all (a zero limit is a cash customer). */
    public boolean creditCustomer() {
        return creditLimit.signum() > 0;
    }

    /** Share of the credit line already used, 0 when there is no limit. */
    public int utilizationPercent() {
        if (!creditCustomer()) {
            return 0;
        }
        return Math.min(100, balance.multiply(BigDecimal.valueOf(100))
                .divide(creditLimit, 0, java.math.RoundingMode.HALF_UP).intValue());
    }

    /** The statement of account: invoices on the debit side, payments and credits on the other. */
    public record Statement(Customer customer, LocalDate from, LocalDate to, List<Line> lines,
                            BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal closingBalance) {

        public Statement {
            lines = lines == null ? List.of() : List.copyOf(lines);
            totalDebit = totalDebit == null ? BigDecimal.ZERO : totalDebit;
            totalCredit = totalCredit == null ? BigDecimal.ZERO : totalCredit;
            closingBalance = closingBalance == null ? BigDecimal.ZERO : closingBalance;
        }

        /** One movement of the statement, with the balance after it. */
        public record Line(String kind, long documentId, String documentNumber, LocalDate documentDate,
                           String status, BigDecimal debit, BigDecimal credit, BigDecimal balance) {

            public Line {
                debit = debit == null ? BigDecimal.ZERO : debit;
                credit = credit == null ? BigDecimal.ZERO : credit;
                balance = balance == null ? BigDecimal.ZERO : balance;
            }
        }
    }

    /** The fields a user may fill in for a new customer (the server prices and codes the rest). */
    public record NewCustomer(String name, String type, String phone, String email, String address,
                              String wilaya, java.math.BigDecimal creditLimit, String priceSegment,
                              String notes, Boolean active) {
    }
}
