package com.chemmanager.dto;

import java.math.BigDecimal;
import java.util.List;

/** Version-to-version diff: what changed material by material and what it costs. */
public record FormulationComparison(
        int leftVersion,
        int rightVersion,
        BigDecimal leftCost,
        BigDecimal rightCost,
        BigDecimal costDelta,
        List<Line> lines) {

    public FormulationComparison {
        leftCost = leftCost == null ? BigDecimal.ZERO : leftCost;
        rightCost = rightCost == null ? BigDecimal.ZERO : rightCost;
        costDelta = costDelta == null ? BigDecimal.ZERO : costDelta;
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record Line(String materialCode, String materialName, String unit, BigDecimal leftQuantity,
                       BigDecimal rightQuantity, BigDecimal delta, String status) {

        public boolean changed() {
            return !"UNCHANGED".equals(status);
        }
    }
}
