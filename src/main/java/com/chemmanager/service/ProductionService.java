package com.chemmanager.service;

import java.util.List;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductionQuery;
import com.chemmanager.model.production.ApprovedVersion;
import com.chemmanager.model.production.ProductionGenealogy;
import com.chemmanager.model.production.ProductionOrder;

/**
 * Manufacturing orders end to end: the approved versions a run may start from, the reservations,
 * the real consumption, the quality release and the batch genealogy.
 *
 * <p>The client never computes a formula, a cost or a release decision: it shows what the server
 * reports and sends what the staff recorded.</p>
 */
public interface ProductionService {

    List<ApprovedVersion> versions();

    PagedResult<ProductionOrder> search(ProductionQuery query);

    ProductionOrder load(long id);

    ProductionOrder create(ProductionOrder.NewOrder request);

    ProductionOrder start(long id);

    ProductionOrder consume(long id, List<ProductionOrder.Consumption> items);

    ProductionOrder complete(long id, ProductionOrder.Completion completion);

    ProductionOrder quality(long id, ProductionOrder.QualityDecision decision);

    ProductionOrder cancel(long id, String reason);

    ProductionGenealogy genealogy(long id);
}
