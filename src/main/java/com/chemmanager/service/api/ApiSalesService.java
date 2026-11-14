package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SalesQuery;
import com.chemmanager.model.sales.CashAccount;
import com.chemmanager.model.sales.DeliveryNote;
import com.chemmanager.model.sales.Invoice;
import com.chemmanager.model.sales.Quotation;
import com.chemmanager.model.sales.SalesOrder;
import com.chemmanager.service.SalesService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sales served by {@code /api/quotations}, {@code /api/salesOrders}, {@code /api/deliveries} and
 * {@code /api/invoices}.
 */
public final class ApiSalesService implements SalesService {

    private final ApiClient client;

    public ApiSalesService(ApiClient client) {
        this.client = client;
    }

    // ---------------------------------------------------------------- quotations

    @Override
    public PagedResult<Quotation> quotations(SalesQuery query) {
        return page(ApiEndpoints.QUOTATIONS, query).map(ApiSalesService::quotation);
    }

    @Override
    public Quotation quotation(long id) {
        return quotation(client.get(ApiEndpoints.QUOTATIONS + "/" + id, JsonNode.class));
    }

    @Override
    public Quotation createQuotation(Quotation.NewQuotation request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", request.customerId());
        if (request.validUntil() != null) {
            body.put("validUntil", request.validUntil().toString());
        }
        body.put("notes", request.notes() == null ? "" : request.notes());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Quotation.NewLine line : request.items()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", line.productId());
            item.put("quantity", line.quantity());
            if (line.unitPrice() != null && line.unitPrice().signum() > 0) {
                item.put("unitPrice", line.unitPrice());
            }
            if (line.discountPct() != null) {
                item.put("discountPct", line.discountPct());
            }
            items.add(item);
        }
        body.put("items", items);

        return quotation(client.post(ApiEndpoints.QUOTATIONS, body, JsonNode.class));
    }

    @Override
    public Quotation sendQuotation(long id) {
        return quotation(client.post(ApiEndpoints.QUOTATIONS + "/" + id + "/send", Map.of(), JsonNode.class));
    }

    @Override
    public Quotation acceptQuotation(long id) {
        JsonNode node = client.post(ApiEndpoints.QUOTATIONS + "/" + id + "/accept", Map.of(), JsonNode.class);
        return quotation(node.path("quotation"));
    }

    @Override
    public Quotation rejectQuotation(long id) {
        return quotation(client.post(ApiEndpoints.QUOTATIONS + "/" + id + "/reject", Map.of(), JsonNode.class));
    }

    // -------------------------------------------------------------------- orders

    @Override
    public PagedResult<SalesOrder> orders(SalesQuery query) {
        return page(ApiEndpoints.SALES_ORDERS, query).map(ApiSalesService::order);
    }

    @Override
    public SalesOrder order(long id) {
        return order(client.get(ApiEndpoints.SALES_ORDERS + "/" + id, JsonNode.class));
    }

    @Override
    public SalesOrder createOrder(SalesOrder.NewOrder request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", request.customerId());
        if (request.deliveryDate() != null) {
            body.put("deliveryDate", request.deliveryDate().toString());
        }
        body.put("notes", request.notes() == null ? "" : request.notes());
        List<Map<String, Object>> items = new ArrayList<>();
        for (SalesOrder.NewLine line : request.items()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", line.productId());
            item.put("quantity", line.quantity());
            if (line.discountPct() != null) {
                item.put("discountPct", line.discountPct());
            }
            items.add(item);
        }
        body.put("items", items);

        return order(client.post(ApiEndpoints.SALES_ORDERS, body, JsonNode.class));
    }

    @Override
    public SalesOrder confirmOrder(long id) {
        return order(client.post(ApiEndpoints.SALES_ORDERS + "/" + id + "/confirm", Map.of(), JsonNode.class));
    }

    @Override
    public SalesOrder cancelOrder(long id) {
        return order(client.post(ApiEndpoints.SALES_ORDERS + "/" + id + "/cancel", Map.of(), JsonNode.class));
    }

    // ---------------------------------------------------------------- deliveries

    @Override
    public PagedResult<DeliveryNote> deliveries(SalesQuery query) {
        return page(ApiEndpoints.DELIVERIES, query).map(ApiSalesService::delivery);
    }

    @Override
    public DeliveryNote delivery(long id) {
        return delivery(client.get(ApiEndpoints.DELIVERIES + "/" + id, JsonNode.class));
    }

    @Override
    public DeliveryNote createDelivery(long orderId, List<DeliveryLine> lines) {
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        for (DeliveryLine line : lines) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderItemId", line.orderItemId());
            item.put("quantity", line.quantity());
            items.add(item);
        }
        if (!items.isEmpty()) {
            body.put("items", items);
        }

        return delivery(client.post(ApiEndpoints.SALES_ORDERS + "/" + orderId + "/deliveries", body,
                JsonNode.class));
    }

    @Override
    public DeliveryNote loadDelivery(long id) {
        return delivery(client.post(ApiEndpoints.DELIVERIES + "/" + id + "/load", Map.of(), JsonNode.class));
    }

    @Override
    public DeliveryNote signDelivery(long id, String signedBy, String notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("signedBy", signedBy);
        body.put("notes", notes == null ? "" : notes);

        return delivery(client.post(ApiEndpoints.DELIVERIES + "/" + id + "/deliver", body, JsonNode.class));
    }

    // ------------------------------------------------------------------ invoices

    @Override
    public PagedResult<Invoice> invoices(SalesQuery query) {
        return page(ApiEndpoints.INVOICES, query).map(ApiSalesService::invoice);
    }

    @Override
    public Invoice invoice(long id) {
        return invoice(client.get(ApiEndpoints.INVOICES + "/" + id, JsonNode.class));
    }

    @Override
    public Invoice createInvoice(long orderId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);

        return invoice(client.post(ApiEndpoints.INVOICES, body, JsonNode.class));
    }

    @Override
    public Invoice issueInvoice(long id) {
        return invoice(client.post(ApiEndpoints.INVOICES + "/" + id + "/issue", Map.of(), JsonNode.class));
    }

    @Override
    public Invoice cancelInvoice(long id) {
        return invoice(client.post(ApiEndpoints.INVOICES + "/" + id + "/cancel", Map.of(), JsonNode.class));
    }

    // ------------------------------------------------------------------ payments

    @Override
    public List<CashAccount> cashAccounts() {
        JsonNode rows = client.get(ApiEndpoints.CASH_ACCOUNTS, JsonNode.class);
        List<CashAccount> accounts = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                accounts.add(new CashAccount(row.path("id").asLong(), text(row, "code"), text(row, "name"),
                        text(row, "type"), text(row, "bankName")));
            }
        }

        return accounts;
    }

    @Override
    public Invoice.Payment pay(NewPayment request) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (request.invoiceId() > 0) {
            body.put("invoiceId", request.invoiceId());
        } else {
            body.put("customerId", request.customerId());
        }
        body.put("amount", request.amount());
        body.put("cashAccountId", request.cashAccountId());
        body.put("method", request.method() == null ? "CASH" : request.method());
        body.put("reference", request.reference() == null ? "" : request.reference());

        return payment(client.post(ApiEndpoints.PAYMENTS, body, JsonNode.class));
    }

    // -------------------------------------------------------------- credit notes

    @Override
    public Invoice.CreditNote creditNote(long invoiceId, String reason, BigDecimal amountHt, BigDecimal vatRate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        body.put("amountHt", amountHt);
        if (vatRate != null) {
            body.put("vatRate", vatRate);
        }

        return creditNote(client.post(ApiEndpoints.INVOICES + "/" + invoiceId + "/creditNotes", body,
                JsonNode.class));
    }

    @Override
    public Invoice.CreditNote applyCreditNote(long id) {
        return creditNote(client.post(ApiEndpoints.CREDIT_NOTES + "/" + id + "/apply", Map.of(), JsonNode.class));
    }

    @Override
    public Invoice.CreditNote cancelCreditNote(long id) {
        return creditNote(client.post(ApiEndpoints.CREDIT_NOTES + "/" + id + "/cancel", Map.of(), JsonNode.class));
    }

    // -------------------------------------------------------------------- mapping

    private PagedResult<JsonNode> page(String path, SalesQuery query) {
        SalesQuery effective = query == null ? SalesQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("status", effective.status());
        if (effective.customerId() > 0) {
            params.put("customerId", Long.toString(effective.customerId()));
        }
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(path, params, JsonNode.class);
    }

    static Quotation quotation(JsonNode node) {
        return new Quotation(
                node.path("id").asLong(),
                text(node, "quoteNumber"),
                node.path("customerId").asLong(),
                text(node, "customerName"),
                text(node, "status"),
                date(node, "quoteDate"),
                date(node, "validUntil"),
                decimal(node, "amountHt"),
                decimal(node, "vatAmount"),
                decimal(node, "totalTtc"),
                text(node, "notes"),
                items(node).stream().map(row -> new Quotation.Line(
                        row.path("id").asLong(), row.path("productId").asLong(), text(row, "productCode"),
                        text(row, "productName"), text(row, "unit"), decimal(row, "quantity"),
                        decimal(row, "unitPrice"), decimal(row, "discountPct"), decimal(row, "vatRate"),
                        decimal(row, "lineTotal"))).toList());
    }

    static SalesOrder order(JsonNode node) {
        List<SalesOrder.Ref> deliveries = new ArrayList<>();
        for (JsonNode row : node.path("deliveries")) {
            deliveries.add(new SalesOrder.Ref(row.path("id").asLong(), text(row, "noteNumber"),
                    text(row, "status"), date(row, "preparedAt"), BigDecimal.ZERO));
        }
        List<SalesOrder.Ref> invoices = new ArrayList<>();
        for (JsonNode row : node.path("invoices")) {
            invoices.add(new SalesOrder.Ref(row.path("id").asLong(), text(row, "invoiceNumber"),
                    text(row, "status"), date(row, "invoiceDate"), decimal(row, "totalTtc")));
        }

        return new SalesOrder(
                node.path("id").asLong(),
                text(node, "orderNumber"),
                node.path("customerId").asLong(),
                text(node, "customerName"),
                text(node, "customerCode"),
                text(node, "status"),
                date(node, "orderDate"),
                date(node, "deliveryDate"),
                decimal(node, "amountHt"),
                decimal(node, "vatAmount"),
                decimal(node, "totalTtc"),
                decimal(node, "orderedQty"),
                decimal(node, "deliveredQty"),
                decimal(node, "invoicedQty"),
                node.path("deliveryProgress").asDouble(),
                node.path("invoiceProgress").asDouble(),
                text(node, "notes"),
                items(node).stream().map(row -> new SalesOrder.Line(
                        row.path("id").asLong(), row.path("productId").asLong(), text(row, "productCode"),
                        text(row, "productName"), text(row, "unit"), decimal(row, "quantity"),
                        decimal(row, "deliveredQty"), decimal(row, "invoicedQty"), decimal(row, "unitPrice"),
                        decimal(row, "discountPct"), decimal(row, "vatRate"), decimal(row, "lineTotal"))).toList(),
                deliveries,
                invoices);
    }

    static DeliveryNote delivery(JsonNode node) {
        List<DeliveryNote.Line> items = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            items.add(new DeliveryNote.Line(row.path("id").asLong(), row.path("productId").asLong(),
                    text(row, "productCode"), text(row, "productName"), text(row, "unit"),
                    row.path("productBatchId").asLong(), text(row, "batchNumber"), decimal(row, "quantity")));
        }

        return new DeliveryNote(
                node.path("id").asLong(),
                text(node, "noteNumber"),
                node.path("orderId").asLong(),
                text(node, "orderNumber"),
                node.path("customerId").asLong(),
                text(node, "customerName"),
                text(node, "status"),
                date(node, "preparedAt"),
                date(node, "deliveredAt"),
                text(node, "signedBy"),
                text(node, "routeNumber"),
                text(node, "notes"),
                decimal(node, "totalQuantity"),
                node.path("lineCount").asInt(items.size()),
                items);
    }

    static Invoice invoice(JsonNode node) {
        List<Invoice.Line> items = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            items.add(new Invoice.Line(row.path("id").asLong(), row.path("productId").asLong(),
                    text(row, "productCode"), text(row, "productName"), text(row, "unit"),
                    text(row, "batchNumber"), decimal(row, "quantity"), decimal(row, "unitPrice"),
                    decimal(row, "discountPct"), decimal(row, "vatRate"), decimal(row, "lineTotal")));
        }
        List<Invoice.Payment> payments = new ArrayList<>();
        for (JsonNode row : node.path("payments")) {
            payments.add(payment(row));
        }
        List<Invoice.CreditNote> creditNotes = new ArrayList<>();
        for (JsonNode row : node.path("creditNotes")) {
            creditNotes.add(creditNote(row));
        }

        return new Invoice(
                node.path("id").asLong(),
                text(node, "invoiceNumber"),
                node.path("customerId").asLong(),
                text(node, "customerName"),
                node.path("orderId").asLong(),
                text(node, "orderNumber"),
                text(node, "status"),
                date(node, "invoiceDate"),
                date(node, "dueDate"),
                decimal(node, "amountHt"),
                decimal(node, "vatAmount"),
                decimal(node, "totalTtc"),
                decimal(node, "paidAmount"),
                decimal(node, "remaining"),
                node.path("isFinalized").asBoolean(false),
                node.path("isOverdue").asBoolean(false),
                node.path("printedCount").asInt(),
                text(node, "notes"),
                items,
                payments,
                creditNotes);
    }

    static Invoice.Payment payment(JsonNode node) {
        return new Invoice.Payment(
                node.path("id").asLong(),
                text(node, "paymentNumber"),
                node.hasNonNull("invoiceId") ? node.path("invoiceId").asLong() : null,
                text(node, "invoiceNumber"),
                node.path("customerId").asLong(),
                text(node, "customerName"),
                decimal(node, "amount"),
                date(node, "paidAt"),
                text(node, "method"),
                text(node, "reference"),
                text(node, "cashAccountName"));
    }

    static Invoice.CreditNote creditNote(JsonNode node) {
        return new Invoice.CreditNote(
                node.path("id").asLong(),
                text(node, "creditNumber"),
                node.path("invoiceId").asLong(),
                text(node, "invoiceNumber"),
                node.path("customerId").asLong(),
                text(node, "customerName"),
                date(node, "creditDate"),
                text(node, "reason"),
                decimal(node, "amountHt"),
                decimal(node, "vatAmount"),
                decimal(node, "totalTtc"),
                text(node, "status"));
    }

    /** The rows of an array property, as a list so they can be streamed. */
    private static List<JsonNode> items(JsonNode node) {
        List<JsonNode> rows = new ArrayList<>();
        for (JsonNode row : node.path("items")) {
            rows.add(row);
        }

        return rows;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    static LocalDate date(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        String raw = value.asText();
        try {
            return LocalDate.parse(raw.length() > 10 ? raw.substring(0, 10) : raw);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
