package com.chemmanager.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One version of a formulation. An approved version is locked: the user interface must offer
 * "new version" instead of an edit, and the API refuses the write with {@code 409} anyway.
 */
public record FormulationVersion(
        long id,
        long formulationId,
        int version,
        String instructions,
        BigDecimal expectedYield,
        String yieldUnit,
        boolean locked,
        String approvedByName,
        LocalDateTime approvedAt,
        BigDecimal cost,
        int itemCount,
        List<FormulationItem> items) {

    public FormulationVersion {
        expectedYield = expectedYield == null ? BigDecimal.ZERO : expectedYield;
        cost = cost == null ? BigDecimal.ZERO : cost;
        items = items == null ? List.of() : List.copyOf(items);
    }

    public String label() {
        return "v" + version;
    }

    public boolean editable() {
        return !locked;
    }

    /** Sum of the quantities, used as a sanity check against the declared yield. */
    public BigDecimal totalQuantity() {
        BigDecimal total = BigDecimal.ZERO;
        for (FormulationItem item : items) {
            total = total.add(item.quantity());
        }
        return total;
    }
}
