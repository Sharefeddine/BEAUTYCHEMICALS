package com.chemmanager.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A formulation family: the identity of a formula, its approval state and the version currently
 * in force. The content (raw materials, quantities, instructions) lives in
 * {@link FormulationVersion}, because an approved version must never change.
 *
 * <p>The application never computes a formula: a version exists only because a qualified user
 * entered its bill of materials.</p>
 */
public record Formulation(
        long id,
        String code,
        String nameFr,
        String nameAr,
        Long productId,
        String productName,
        String status,
        Long currentVersionId,
        Integer currentVersion,
        BigDecimal currentCost,
        BigDecimal currentYield,
        String currentYieldUnit,
        int versionCount,
        String approvedByName,
        String notes,
        LocalDateTime updatedAt) {

    public Formulation {
        currentCost = currentCost == null ? BigDecimal.ZERO : currentCost;
        currentYield = currentYield == null ? BigDecimal.ZERO : currentYield;
    }

    /** The name in the language of the interface, falling back to the other one. */
    public String displayName(boolean arabic) {
        if (arabic) {
            return nameAr == null || nameAr.isBlank() ? nameFr : nameAr;
        }
        return nameFr == null || nameFr.isBlank() ? nameAr : nameFr;
    }

    public boolean approved() {
        return "APPROVED".equalsIgnoreCase(status);
    }

    public boolean archived() {
        return "ARCHIVED".equalsIgnoreCase(status);
    }

    public boolean draft() {
        return "DRAFT".equalsIgnoreCase(status);
    }

    public String versionLabel() {
        return currentVersion == null ? "" : "v" + currentVersion;
    }
}
