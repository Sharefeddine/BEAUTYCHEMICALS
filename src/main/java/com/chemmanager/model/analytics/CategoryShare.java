package com.chemmanager.model.analytics;

import java.math.BigDecimal;

/** Revenue or valuation group, used by pie / donut charts. */
public record CategoryShare(String label, BigDecimal value) {

    public CategoryShare {
        value = value == null ? BigDecimal.ZERO : value;
    }
}
