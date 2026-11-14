package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.CustomerQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.model.sales.Customer;
import com.chemmanager.service.CustomerService;
import com.fasterxml.jackson.databind.JsonNode;

/** Customers served by {@code /api/customers}. */
public final class ApiCustomerService implements CustomerService {

    private final ApiClient client;

    public ApiCustomerService(ApiClient client) {
        this.client = client;
    }

    @Override
    public PagedResult<Customer> search(CustomerQuery query) {
        CustomerQuery effective = query == null ? CustomerQuery.initial(25) : query;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("search", effective.search());
        params.put("priceSegment", effective.segment());
        params.put("page", Integer.toString(effective.page() + 1));
        params.put("pageSize", Integer.toString(effective.pageSize()));

        return client.getPage(ApiEndpoints.CUSTOMERS, params, JsonNode.class).map(ApiCustomerService::customer);
    }

    @Override
    public Customer load(long id) {
        return customer(client.get(ApiEndpoints.CUSTOMERS + "/" + id, JsonNode.class));
    }

    @Override
    public Customer.Statement statement(long id) {
        JsonNode node = client.get(ApiEndpoints.CUSTOMERS + "/" + id + "/statement", JsonNode.class);
        List<Customer.Statement.Line> lines = new ArrayList<>();
        for (JsonNode row : node.path("lines")) {
            lines.add(new Customer.Statement.Line(
                    text(row, "kind"),
                    row.path("documentId").asLong(),
                    text(row, "documentNumber"),
                    date(row, "documentDate"),
                    text(row, "status"),
                    decimal(row, "debit"),
                    decimal(row, "credit"),
                    decimal(row, "balance")));
        }

        return new Customer.Statement(customer(node.path("customer")), date(node, "from"), date(node, "to"), lines,
                decimal(node, "totalDebit"), decimal(node, "totalCredit"), decimal(node, "closingBalance"));
    }

    @Override
    public List<Customer> selectable() {
        JsonNode rows = client.get(ApiEndpoints.CUSTOMERS + "/selectable", JsonNode.class);
        List<Customer> customers = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                customers.add(customer(row));
            }
        }

        return customers;
    }

    @Override
    public Customer create(Customer.NewCustomer request) {
        return customer(client.post(ApiEndpoints.CUSTOMERS, body(request), JsonNode.class));
    }

    @Override
    public Customer update(long id, Customer.NewCustomer request) {
        return customer(client.put(ApiEndpoints.CUSTOMERS + "/" + id, body(request), JsonNode.class));
    }

    @Override
    public Customer deactivate(long id) {
        return customer(client.delete(ApiEndpoints.CUSTOMERS + "/" + id, JsonNode.class));
    }

    private static Map<String, Object> body(Customer.NewCustomer request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", request.name());
        body.put("type", request.type());
        body.put("phone", request.phone() == null ? "" : request.phone());
        body.put("email", request.email() == null ? "" : request.email());
        body.put("address", request.address() == null ? "" : request.address());
        body.put("wilaya", request.wilaya() == null ? "" : request.wilaya());
        body.put("creditLimit", request.creditLimit() == null ? BigDecimal.ZERO : request.creditLimit());
        body.put("priceSegment", request.priceSegment());
        body.put("notes", request.notes() == null ? "" : request.notes());
        if (request.active() != null) {
            body.put("active", request.active());
        }

        return body;
    }

    static Customer customer(JsonNode node) {
        return new Customer(
                node.path("id").asLong(),
                text(node, "code"),
                text(node, "name"),
                text(node, "type"),
                text(node, "phone"),
                text(node, "email"),
                text(node, "address"),
                text(node, "wilaya"),
                decimal(node, "creditLimit"),
                decimal(node, "balance"),
                decimal(node, "availableCredit"),
                text(node, "priceSegment"),
                text(node, "notes"),
                node.path("isActive").asBoolean(true),
                node.path("invoiceCount").asInt(),
                node.path("orderCount").asInt(),
                decimalOrZero(node, "outstanding"));
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    static BigDecimal decimalOrZero(JsonNode node, String field) {
        BigDecimal value = decimal(node, field);
        return value == null ? BigDecimal.ZERO : value;
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
