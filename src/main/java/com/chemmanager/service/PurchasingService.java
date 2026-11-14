package com.chemmanager.service;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.PurchaseQuery;
import com.chemmanager.model.purchasing.GoodsReceipt;
import com.chemmanager.model.purchasing.PurchaseOrder;
import com.chemmanager.model.purchasing.PurchaseOrderDraft;
import com.chemmanager.model.purchasing.ReceptionDraft;

/** Purchase orders and the goods receptions that put stock on the shelves. */
public interface PurchasingService {

    PagedResult<PurchaseOrder> search(PurchaseQuery query);

    PurchaseOrder load(long id);

    PurchaseOrder create(PurchaseOrderDraft draft);

    PurchaseOrder send(long id);

    PurchaseOrder cancel(long id);

    GoodsReceipt receive(long orderId, ReceptionDraft draft);
}
