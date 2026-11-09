package com.chemmanager.model;

import java.math.BigDecimal;

/**
 * One line of a bill of materials: a raw material, the quantity the production manager entered,
 * the phase in which it is incorporated and the cost at the current purchase price.
 */
public record FormulationItem(
        long materialId,
        String materialCode,
        String materialName,
        BigDecimal quantity,
        String unit,
        BigDecimal percentage,
        String phase,
        BigDecimal lineCost,
        BigDecimal stockQuantity,
        String hazardClass) {

    public FormulationItem {
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
        percentage = percentage == null ? BigDecimal.ZERO : percentage;
        lineCost = lineCost == null ? BigDecimal.ZERO : lineCost;
        stockQuantity = stockQuantity == null ? BigDecimal.ZERO : stockQuantity;
    }

    /** A structured line as typed in the editor, without the derived cost and stock columns. */
    public static FormulationItem entry(long materialId, String code, String name, BigDecimal quantity,
                                        String unit, String phase) {
        return new FormulationItem(materialId, code, name, quantity, unit, BigDecimal.ZERO, phase,
                BigDecimal.ZERO, BigDecimal.ZERO, "");
    }

    public FormulationItem withQuantity(BigDecimal value) {
        return new FormulationItem(materialId, materialCode, materialName, value, unit, percentage, phase,
                lineCost, stockQuantity, hazardClass);
    }

    public boolean dangerous() {
        return hazardClass != null && !hazardClass.isBlank() && !"NONE".equalsIgnoreCase(hazardClass);
    }
}
