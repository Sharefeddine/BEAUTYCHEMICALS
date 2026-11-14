package com.chemmanager.model.production;

import java.math.BigDecimal;

/**
 * An approved, locked formulation version — the only thing a production order may start from.
 * The application never proposes a formula; it offers what a manager approved.
 */
public record ApprovedVersion(long versionId, int version, String formulationCode, String nameFr,
                              String nameAr, long productId, String productName, BigDecimal expectedYield,
                              String yieldUnit, int itemCount) {
}
