package com.chemmanager.service;

import java.math.BigDecimal;
import java.util.List;

import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SalesQuery;
import com.chemmanager.model.sales.CashAccount;
import com.chemmanager.model.sales.DeliveryNote;
import com.chemmanager.model.sales.Invoice;
import com.chemmanager.model.sales.Quotation;
import com.chemmanager.model.sales.SalesOrder;

/**
 * The commercial chain: quotation, order, delivery note, invoice, payment and credit note.
 *
 * <p>Every rule lives on the server — the credit limit, the FEFO batch picked for a delivery, the
 * invoice that cannot be modified once issued — and this service only carries the user's decisions
 * there. The client never marks an order as delivered: it asks for a delivery note and reads back
 * the batches the server consumed.</p>
 */
public interface SalesService {

    // ---------------------------------------------------------------- quotations
    PagedResult<Quotation> quotations(SalesQuery query);

    Quotation quotation(long id);

    Quotation createQuotation(Quotation.NewQuotation request);

    Quotation sendQuotation(long id);

    Quotation acceptQuotation(long id);

    Quotation rejectQuotation(long id);

    // -------------------------------------------------------------------- orders
    PagedResult<SalesOrder> orders(SalesQuery query);

    SalesOrder order(long id);

    SalesOrder createOrder(SalesOrder.NewOrder request);

    SalesOrder confirmOrder(long id);

    SalesOrder cancelOrder(long id);

    // ---------------------------------------------------------------- deliveries
    PagedResult<DeliveryNote> deliveries(SalesQuery query);

    DeliveryNote delivery(long id);

    /** Prepares a delivery; an empty list ships everything still to ship. */
    DeliveryNote createDelivery(long orderId, List<DeliveryLine> lines);

    DeliveryNote loadDelivery(long id);

    DeliveryNote signDelivery(long id, String signedBy, String notes);

    // ------------------------------------------------------------------ invoices
    PagedResult<Invoice> invoices(SalesQuery query);

    Invoice invoice(long id);

    Invoice createInvoice(long orderId);

    Invoice issueInvoice(long id);

    Invoice cancelInvoice(long id);

    // ------------------------------------------------------------------ payments
    List<CashAccount> cashAccounts();

    Invoice.Payment pay(NewPayment request);

    // -------------------------------------------------------------- credit notes
    Invoice.CreditNote creditNote(long invoiceId, String reason, BigDecimal amountHt, BigDecimal vatRate);

    Invoice.CreditNote applyCreditNote(long id);

    Invoice.CreditNote cancelCreditNote(long id);

    /** One line to ship, by order line, so the progress figures stay attached to the right product. */
    record DeliveryLine(long orderItemId, BigDecimal quantity) {
    }

    /** A settlement: against an invoice, or on the customer's account when {@code invoiceId} is 0. */
    record NewPayment(long invoiceId, long customerId, BigDecimal amount, long cashAccountId, String method,
                      String reference) {
    }
}
