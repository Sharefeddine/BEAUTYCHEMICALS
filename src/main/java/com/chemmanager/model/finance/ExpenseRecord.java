package com.chemmanager.model.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An operating expense: entered as a draft, approved, then paid through a cash account — the
 * payment is what writes the cash-book line on the server.
 */
public record ExpenseRecord(long id, String number, String categoryCode, String categoryName, String label,
                            BigDecimal amount, BigDecimal vatAmount, BigDecimal totalAmount, LocalDate expenseDate,
                            String paymentMethod, String cashAccountName, String employeeName, String supplierName,
                            String vehiclePlate, String status, String notes, String creatorName,
                            String approvedByName) {

    public boolean isDraft() {
        return "DRAFT".equals(status);
    }

    public boolean isPaid() {
        return "PAID".equals(status);
    }

    public boolean isCancelled() {
        return "CANCELLED".equals(status);
    }

    /** A draft can be approved, a draft or an approved expense can be paid, a paid one cannot. */
    public boolean canBeApproved() {
        return isDraft();
    }

    public boolean canBePaid() {
        return isDraft() || "APPROVED".equals(status);
    }

    public boolean canBeCancelled() {
        return isDraft() || "APPROVED".equals(status);
    }
}
