package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.DateRange;
import com.chemmanager.model.Severity;
import com.chemmanager.model.analytics.ActivityEntry;
import com.chemmanager.model.analytics.CategoryShare;
import com.chemmanager.model.analytics.CustomerPerformance;
import com.chemmanager.model.analytics.DashboardData;
import com.chemmanager.model.analytics.KpiSet;
import com.chemmanager.model.analytics.MonthPoint;
import com.chemmanager.model.analytics.StockAlert;
import com.chemmanager.model.analytics.StockAlertType;
import com.chemmanager.model.analytics.TopProduct;
import com.chemmanager.service.DashboardService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Dashboard served by {@code GET /api/dashboard/summary}.
 *
 * <p>The server aggregates every figure from the ledger tables (invoices, purchase orders,
 * expenses, batches, production orders, audit log) over the requested period; this class only
 * renames the payload into the client records. An empty database legitimately produces zeros and
 * empty series — nothing is invented to fill a chart.</p>
 */
public final class ApiDashboardService implements DashboardService {

    private final ApiClient client;

    public ApiDashboardService(ApiClient client) {
        this.client = client;
    }

    @Override
    public DashboardData load(DateRange range) {
        Map<String, String> query = new LinkedHashMap<>();
        if (range != null) {
            query.put("from", range.from().toString());
            query.put("to", range.to().toString());
        }
        JsonNode payload = client.get(ApiEndpoints.DASHBOARD, query, JsonNode.class);
        if (payload == null) {
            throw new com.chemmanager.api.ApiException("The dashboard endpoint returned no payload.");
        }

        return new DashboardData(
                range == null ? DateRange.ofMonth(LocalDate.now()) : range,
                kpis(payload.path("kpis")),
                monthPoints(payload.path("monthly")),
                shares(payload.path("salesByCategory")),
                shares(payload.path("inventoryDistribution")),
                products(payload.path("bestSellers")),
                products(payload.path("mostProfitable")),
                customers(payload.path("topCustomers")),
                alerts(payload.path("lowStockAlerts")),
                alerts(payload.path("expiryAlerts")),
                activities(payload.path("recentActivities")),
                ApiMapper.instant(payload, "generatedAt"));
    }

    private static KpiSet kpis(JsonNode node) {
        return new KpiSet(
                decimal(node, "salesToday"),
                decimal(node, "salesMonth"),
                decimal(node, "salesYear"),
                decimal(node, "salesInRange"),
                decimal(node, "purchasesInRange"),
                decimal(node, "expensesInRange"),
                decimal(node, "grossProfitInRange"),
                decimal(node, "netProfitInRange"),
                decimal(node, "inventoryValue"),
                decimal(node, "receivables"),
                decimal(node, "payables"),
                decimal(node, "cashBalance"),
                decimal(node, "productionQuantityInRange"),
                decimal(node, "averageBasket"),
                node.path("lowStockCount").asInt(0),
                node.path("expiringCount").asInt(0),
                node.path("pendingProductionCount").asInt(0),
                node.path("openOrders").asInt(0));
    }

    private static List<MonthPoint> monthPoints(JsonNode array) {
        List<MonthPoint> points = new ArrayList<>();
        for (JsonNode row : array) {
            LocalDate month = LocalDate.parse(row.path("month").asText().substring(0, 10));
            points.add(new MonthPoint(month,
                    decimal(row, "sales"), decimal(row, "purchases"), decimal(row, "expenses"),
                    decimal(row, "productionCost"), decimal(row, "productionQuantity")));
        }
        return points;
    }

    private static List<CategoryShare> shares(JsonNode array) {
        List<CategoryShare> shares = new ArrayList<>();
        for (JsonNode row : array) {
            shares.add(new CategoryShare(row.path("label").asText(""), decimal(row, "value")));
        }
        return shares;
    }

    private static List<TopProduct> products(JsonNode array) {
        List<TopProduct> products = new ArrayList<>();
        for (JsonNode row : array) {
            products.add(new TopProduct(row.path("sku").asText(""), row.path("name").asText(""),
                    row.path("category").asText(""), decimal(row, "quantity"),
                    decimal(row, "revenue"), decimal(row, "profit")));
        }
        return products;
    }

    private static List<CustomerPerformance> customers(JsonNode array) {
        List<CustomerPerformance> customers = new ArrayList<>();
        for (JsonNode row : array) {
            customers.add(new CustomerPerformance(row.path("customer").asText(""),
                    row.path("wilaya").asText(""), decimal(row, "revenue"), decimal(row, "balance"),
                    row.path("orders").asInt(0)));
        }
        return customers;
    }

    private static List<StockAlert> alerts(JsonNode array) {
        List<StockAlert> alerts = new ArrayList<>();
        for (JsonNode row : array) {
            StockAlertType type;
            try {
                type = StockAlertType.valueOf(row.path("type").asText("LOW_STOCK"));
            } catch (IllegalArgumentException ex) {
                type = StockAlertType.LOW_STOCK;
            }
            String expiry = row.path("expiryDate").asText("");
            alerts.add(new StockAlert(
                    row.path("id").asLong(),
                    type,
                    row.path("itemCode").asText(""),
                    row.path("itemName").asText(""),
                    row.path("category").asText(""),
                    decimal(row, "quantity"),
                    decimal(row, "threshold"),
                    row.path("unit").asText(""),
                    expiry.isBlank() ? null : LocalDate.parse(expiry.substring(0, 10)),
                    row.path("warehouse").asText(""),
                    type == StockAlertType.OUT_OF_STOCK || type == StockAlertType.EXPIRED
                            ? Severity.CRITICAL
                            : Severity.WARNING));
        }
        return alerts;
    }

    private static List<ActivityEntry> activities(JsonNode array) {
        List<ActivityEntry> entries = new ArrayList<>();
        for (JsonNode row : array) {
            Severity severity;
            try {
                severity = Severity.valueOf(row.path("severity").asText("INFO"));
            } catch (IllegalArgumentException ex) {
                severity = Severity.INFO;
            }
            entries.add(new ActivityEntry(row.path("id").asLong(), ApiMapper.instant(row, "occurredAt"),
                    row.path("username").asText(""), row.path("action").asText(""),
                    row.path("module").asText(""), row.path("reference").asText(""), severity));
        }
        return entries;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
