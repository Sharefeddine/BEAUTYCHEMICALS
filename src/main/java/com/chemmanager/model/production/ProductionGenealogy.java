package com.chemmanager.model.production;

import java.math.BigDecimal;
import java.util.List;

/**
 * Batch genealogy: a finished batch, the material batches that went into it, and the supplier
 * reception each of those came from. This is what makes a recall answerable in minutes.
 */
public record ProductionGenealogy(
        long orderId,
        String orderNumber,
        String productName,
        String formulationCode,
        int formulationVersion,
        String status,
        String plannedDate,
        boolean complete,
        List<Input> inputs,
        List<ProductionOrder.Output> outputs,
        List<ProductionOrder.QualityCheck> qualityChecks) {

    public ProductionGenealogy {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        qualityChecks = qualityChecks == null ? List.of() : List.copyOf(qualityChecks);
    }

    /** One consumed material batch, traced back to the reception that brought it in. */
    public record Input(String materialCode, String materialName, String batchNumber, BigDecimal plannedQuantity,
                        BigDecimal consumedQuantity, String unit, String supplierName, String receiptNumber,
                        String purchaseOrderNumber, String expiryDate) {
    }
}
