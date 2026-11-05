package com.chemmanager.model.analytics;

import java.math.BigDecimal;

/** Product performance line for the "best sellers" and "most profitable" panels. */
public record TopProduct(String sku, String name, String category, BigDecimal quantity,
                         BigDecimal revenue, BigDecimal profit) {

    public TopProduct {
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        revenue = revenue == null ? BigDecimal.ZERO : revenue;
        profit = profit == null ? BigDecimal.ZERO : profit;
    }

    public String displayName() {
        String name = this.name == null ? sku : this.name;
        return name.length() > 34 ? name.substring(0, 31) + "…" : name;
    }
}
