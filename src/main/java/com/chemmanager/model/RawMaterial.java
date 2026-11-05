package com.chemmanager.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A raw material, surfactant, fragrance, colourant, additive, solvent or packaging component
 * held in a batch-controlled warehouse. The application never generates formulations:
 * it only stores what qualified personnel entered and approved.
 */
public record RawMaterial(
        long id,
        String code,
        String commercialName,
        String chemicalName,
        String category,
        String supplierName,
        String purchaseUnit,
        String storageUnit,
        BigDecimal conversionFactor,
        BigDecimal purchasePrice,
        BigDecimal stock,
        BigDecimal minStock,
        String storageLocation,
        String batchNumber,
        LocalDate productionDate,
        LocalDate expirationDate,
        String hazardClass,
        String hazardPictograms,
        String storageConditions,
        boolean sdsAvailable,
        String sdsFileName,
        boolean active) {

    public RawMaterial {
        purchasePrice = nz(purchasePrice);
        stock = nz(stock);
        minStock = nz(minStock);
        conversionFactor = conversionFactor == null || conversionFactor.signum() == 0
                ? BigDecimal.ONE : conversionFactor;
    }

    public boolean isLowStock() {
        return stock.compareTo(minStock) <= 0;
    }

    public boolean isOutOfStock() {
        return stock.signum() <= 0;
    }

    /** Remaining shelf life in days, {@code null} when the material has no expiry date. */
    public Long daysToExpiry(LocalDate reference) {
        return expirationDate == null ? null : ChronoUnit.DAYS.between(reference, expirationDate);
    }

    public boolean isExpired(LocalDate reference) {
        return expirationDate != null && expirationDate.isBefore(reference);
    }

    public boolean isExpiringWithin(int days, LocalDate reference) {
        if (expirationDate == null) {
            return false;
        }
        long remaining = ChronoUnit.DAYS.between(reference, expirationDate);
        return remaining >= 0 && remaining <= days;
    }

    public boolean isDangerous() {
        return hazardClass != null && !hazardClass.isBlank() && !"NONE".equalsIgnoreCase(hazardClass);
    }

    public BigDecimal stockValue() {
        return stock.multiply(purchasePrice);
    }

    public Severity hazardSeverity() {
        if (hazardClass == null || hazardClass.isBlank()) {
            return Severity.INFO;
        }
        String normalized = hazardClass.toUpperCase();
        if (normalized.contains("TOXIC") || normalized.contains("CORROSIVE") || normalized.contains("FLAMMABLE")) {
            return Severity.CRITICAL;
        }
        if (normalized.contains("IRRITANT") || normalized.contains("OXIDIZING") || normalized.contains("HARMFUL")) {
            return Severity.WARNING;
        }
        return Severity.INFO;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
