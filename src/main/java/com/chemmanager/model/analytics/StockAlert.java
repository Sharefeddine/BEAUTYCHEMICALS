package com.chemmanager.model.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.chemmanager.model.Severity;

/** A low-stock, out-of-stock or expiry alert computed from inventory batches. */
public record StockAlert(
        long id,
        StockAlertType type,
        String itemCode,
        String itemName,
        String category,
        BigDecimal quantity,
        BigDecimal threshold,
        String unit,
        LocalDate expiryDate,
        String warehouse,
        Severity severity) {

    public String contextLabel() {
        return category == null ? "" : category;
    }
}
