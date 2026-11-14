package com.chemmanager.model.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A manufacturing order as the server reports it.
 *
 * <p>The record carries both the plan (the bill of materials scaled to the ordered quantity) and
 * the execution (which batch was consumed, in what quantity), so the screen never has to recompute
 * a business figure: costs, variance and sellability come from the server.</p>
 */
public record ProductionOrder(
        long id,
        String orderNumber,
        int formulationVersion,
        String formulationCode,
        long productId,
        String productCode,
        String productName,
        long warehouseId,
        String warehouseName,
        BigDecimal plannedQuantity,
        BigDecimal actualQuantity,
        BigDecimal wasteQuantity,
        String unit,
        String status,
        LocalDate plannedDate,
        BigDecimal materialCost,
        BigDecimal packagingCost,
        BigDecimal otherCost,
        BigDecimal totalCost,
        BigDecimal costPerUnit,
        BigDecimal standardMaterialCost,
        BigDecimal variance,
        BigDecimal variancePct,
        BigDecimal consumedQuantity,
        boolean sellable,
        String notes,
        List<PlanLine> plan,
        List<MaterialLine> materials,
        List<Output> outputs,
        List<QualityCheck> qualityChecks) {

    public ProductionOrder {
        plan = plan == null ? List.of() : List.copyOf(plan);
        materials = materials == null ? List.of() : List.copyOf(materials);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        qualityChecks = qualityChecks == null ? List.of() : List.copyOf(qualityChecks);
    }

    /** One line of the scaled bill of materials, before anything was reserved. */
    public record PlanLine(long materialId, String materialCode, String materialName, String unit,
                           BigDecimal plannedQuantity, BigDecimal unitPrice) {
    }

    /** A reserved batch and what the operator actually consumed from it. */
    public record MaterialLine(long id, long materialBatchId, String batchNumber, String materialCode,
                               String materialName, BigDecimal plannedQuantity, BigDecimal consumedQuantity,
                               String unit, BigDecimal unitPrice, BigDecimal lineCost,
                               BigDecimal varianceQuantity, LocalDate expiryDate, String qualityStatus) {
    }

    /** The finished batch this order produced. */
    public record Output(long productBatchId, String batchNumber, BigDecimal quantity, String unit,
                         String qualityStatus, String warehouseName, LocalDate productionDate,
                         LocalDate expiryDate, BigDecimal unitCost, boolean sellable) {
    }

    /** A quality decision, with the measurements that justified it. */
    public record QualityCheck(long id, String checkType, String result, String checkedAt, String remarks,
                               String checkedByName) {
    }

    /** What the screen asks the server to start. */
    public record NewOrder(long formulationVersionId, long warehouseId, BigDecimal plannedQuantity,
                           LocalDate plannedDate, String notes) {
    }

    /** What the operator consumed, batch by batch. */
    public record Consumption(long materialBatchId, BigDecimal quantity) {
    }

    /** Closing figures of the run. */
    public record Completion(BigDecimal actualQuantity, BigDecimal wasteQuantity, BigDecimal packagingCost,
                             BigDecimal otherCost, LocalDate expiryDate) {
    }

    /** A quality decision to record. */
    public record QualityDecision(String checkType, String result, String remarks) {
    }
}
