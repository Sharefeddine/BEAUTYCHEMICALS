package com.chemmanager.service.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.analytics.ProductionReport;
import com.chemmanager.model.analytics.PurchaseReport;
import com.chemmanager.model.analytics.ReportCatalogueEntry;
import com.chemmanager.model.analytics.SafetyReport;
import com.chemmanager.model.analytics.SalesReport;
import com.chemmanager.model.analytics.StockReport;
import com.chemmanager.service.ReportCentreService;
import com.fasterxml.jackson.databind.JsonNode;

/** The report centre as the server computes it, served by {@code /api/reports}. */
public final class ApiReportCentreService implements ReportCentreService {

    private final ApiClient client;

    public ApiReportCentreService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<ReportCatalogueEntry> catalogue() {
        JsonNode node = client.get(ApiEndpoints.REPORTS_CATALOGUE, JsonNode.class);
        List<ReportCatalogueEntry> entries = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode row : node) {
                entries.add(new ReportCatalogueEntry(ApiSalesService.text(row, "key"),
                        ApiSalesService.text(row, "group"), row.path("periodic").asBoolean(false)));
            }
        }

        return entries;
    }

    @Override
    public SalesReport sales(LocalDate from, LocalDate to) {
        JsonNode node = client.get(ApiEndpoints.REPORTS_SALES, window(from, to), JsonNode.class);
        JsonNode totals = node == null ? null : node.path("totals");
        List<SalesReport.Month> months = new ArrayList<>();
        List<SalesReport.Customer> customers = new ArrayList<>();
        List<SalesReport.Product> products = new ArrayList<>();
        if (node != null) {
            for (JsonNode row : node.path("byMonth")) {
                months.add(new SalesReport.Month(ApiSalesService.text(row, "period"),
                        row.path("invoiceCount").asInt(),
                        dec(row, "amountHt"), dec(row, "totalTtc")));
            }
            for (JsonNode row : node.path("byCustomer")) {
                customers.add(new SalesReport.Customer(row.path("customerId").asLong(),
                        ApiSalesService.text(row, "name"), row.path("invoiceCount").asInt(),
                        dec(row, "amountHt"), dec(row, "totalTtc"),
                        dec(row, "outstanding")));
            }
            for (JsonNode row : node.path("byProduct")) {
                products.add(new SalesReport.Product(row.path("productId").asLong(),
                        ApiSalesService.text(row, "sku"), ApiSalesService.text(row, "nameFr"),
                        dec(row, "quantity"), dec(row, "revenue")));
            }
        }

        return new SalesReport(date(node, "from"), date(node, "to"),
                new SalesReport.Totals(totals == null ? 0 : totals.path("invoiceCount").asInt(),
                        dec(totals, "amountHt"), dec(totals, "vatAmount"),
                        dec(totals, "totalTtc"), dec(totals, "paidAmount"),
                        dec(totals, "outstanding")),
                months, customers, products);
    }

    @Override
    public PurchaseReport purchases(LocalDate from, LocalDate to) {
        JsonNode node = client.get(ApiEndpoints.REPORTS_PURCHASES, window(from, to), JsonNode.class);
        JsonNode totals = node == null ? null : node.path("totals");
        List<PurchaseReport.Status> statuses = new ArrayList<>();
        List<PurchaseReport.Month> months = new ArrayList<>();
        List<PurchaseReport.Supplier> suppliers = new ArrayList<>();
        if (node != null) {
            for (JsonNode row : node.path("byStatus")) {
                statuses.add(new PurchaseReport.Status(ApiSalesService.text(row, "status"),
                        row.path("orderCount").asInt(), dec(row, "totalTtc")));
            }
            for (JsonNode row : node.path("byMonth")) {
                months.add(new PurchaseReport.Month(ApiSalesService.text(row, "period"),
                        row.path("orderCount").asInt(), dec(row, "totalTtc")));
            }
            for (JsonNode row : node.path("bySupplier")) {
                suppliers.add(new PurchaseReport.Supplier(row.path("supplierId").asLong(),
                        ApiSalesService.text(row, "name"), row.path("orderCount").asInt(),
                        dec(row, "totalTtc")));
            }
        }

        return new PurchaseReport(date(node, "from"), date(node, "to"),
                new PurchaseReport.Totals(totals == null ? 0 : totals.path("orderCount").asInt(),
                        dec(totals, "amountHt"), dec(totals, "vatAmount"),
                        dec(totals, "totalTtc"),
                        totals == null ? 0 : totals.path("receiptCount").asInt(),
                        dec(totals, "receivedQuantity")),
                statuses, months, suppliers);
    }

    @Override
    public StockReport stock(int expiryDays) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("expiryDays", Integer.toString(expiryDays));
        JsonNode node = client.get(ApiEndpoints.REPORTS_STOCK, params, JsonNode.class);
        JsonNode totals = node == null ? null : node.path("totals");
        List<StockReport.Warehouse> warehouses = new ArrayList<>();
        List<StockReport.Expiring> expiring = new ArrayList<>();
        List<StockReport.LowStock> lowStock = new ArrayList<>();
        if (node != null) {
            for (JsonNode row : node.path("warehouses")) {
                warehouses.add(new StockReport.Warehouse(row.path("id").asLong(),
                        ApiSalesService.text(row, "code"), ApiSalesService.text(row, "name"),
                        dec(row, "productValue"),
                        dec(row, "materialValue"),
                        dec(row, "totalValue")));
            }
            for (JsonNode row : node.path("expiring")) {
                expiring.add(new StockReport.Expiring(ApiSalesService.text(row, "productName"),
                        ApiSalesService.text(row, "batchNumber"), ApiSalesService.text(row, "warehouseName"),
                        dec(row, "quantity"), date(row, "expiryDate"),
                        row.path("daysLeft").asInt(), ApiSalesService.text(row, "qualityStatus")));
            }
            for (JsonNode row : node.path("lowStock")) {
                lowStock.add(new StockReport.LowStock(ApiSalesService.text(row, "sku"),
                        ApiSalesService.text(row, "nameFr"), dec(row, "minStock"),
                        dec(row, "quantity")));
            }
        }

        return new StockReport(new StockReport.Totals(
                dec(totals, "totalValue"),
                totals == null ? 0 : totals.path("expiringCount").asInt(),
                totals == null ? 0 : totals.path("expiredCount").asInt(),
                totals == null ? 0 : totals.path("quarantineCount").asInt(),
                totals == null ? 0 : totals.path("lowStockCount").asInt()),
                warehouses, expiring, lowStock);
    }

    @Override
    public ProductionReport production(LocalDate from, LocalDate to) {
        JsonNode node = client.get(ApiEndpoints.REPORTS_PRODUCTION, window(from, to), JsonNode.class);
        JsonNode totals = node == null ? null : node.path("totals");
        List<ProductionReport.Status> statuses = new ArrayList<>();
        List<ProductionReport.Product> products = new ArrayList<>();
        if (node != null) {
            for (JsonNode row : node.path("byStatus")) {
                statuses.add(new ProductionReport.Status(ApiSalesService.text(row, "status"),
                        row.path("orderCount").asInt(), dec(row, "actualQuantity")));
            }
            for (JsonNode row : node.path("byProduct")) {
                products.add(new ProductionReport.Product(ApiSalesService.text(row, "sku"),
                        ApiSalesService.text(row, "nameFr"), row.path("orderCount").asInt(),
                        dec(row, "plannedQuantity"),
                        dec(row, "actualQuantity"),
                        dec(row, "wasteQuantity"),
                        dec(row, "totalCost")));
            }
        }

        return new ProductionReport(date(node, "from"), date(node, "to"),
                new ProductionReport.Totals(totals == null ? 0 : totals.path("orderCount").asInt(),
                        dec(totals, "plannedQuantity"),
                        dec(totals, "actualQuantity"),
                        dec(totals, "wasteQuantity"),
                        dec(totals, "totalCost"),
                        dec(totals, "yieldPct"),
                        dec(totals, "unitCost")),
                statuses, products);
    }

    @Override
    public SafetyReport safety() {
        JsonNode node = client.get(ApiEndpoints.REPORTS_SAFETY, JsonNode.class);
        JsonNode materials = node == null ? null : node.path("materials");
        JsonNode incidents = node == null ? null : node.path("incidents");
        List<SafetyReport.Severity> severities = new ArrayList<>();
        List<SafetyReport.Type> types = new ArrayList<>();
        List<SafetyReport.MissingSheet> missing = new ArrayList<>();
        if (incidents != null) {
            for (JsonNode row : incidents.path("bySeverity")) {
                severities.add(new SafetyReport.Severity(ApiSalesService.text(row, "severity"),
                        ApiSalesService.text(row, "status"), row.path("incidentCount").asInt()));
            }
            for (JsonNode row : incidents.path("byType")) {
                types.add(new SafetyReport.Type(ApiSalesService.text(row, "incidentType"),
                        row.path("incidentCount").asInt()));
            }
        }
        if (node != null) {
            for (JsonNode row : node.path("missingSheets")) {
                missing.add(new SafetyReport.MissingSheet(ApiSalesService.text(row, "code"),
                        ApiSalesService.text(row, "commercialName"), ApiSalesService.text(row, "hazardClass")));
            }
        }

        return new SafetyReport(new SafetyReport.Materials(
                materials == null ? 0 : materials.path("total").asInt(),
                materials == null ? 0 : materials.path("hazardous").asInt(),
                materials == null ? 0 : materials.path("withoutSheet").asInt(),
                materials == null ? 0 : materials.path("obsoleteSheets").asInt()),
                new SafetyReport.Incidents(severities, types,
                        incidents == null ? 0 : incidents.path("total").asInt()),
                missing);
    }

    private static BigDecimal dec(JsonNode node, String field) {
        if (node == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal value = ApiSalesService.decimal(node, field);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static LocalDate date(JsonNode node, String field) {
        return node == null ? null : ApiSalesService.date(node, field);
    }

    private static Map<String, String> window(LocalDate from, LocalDate to) {
        Map<String, String> params = new LinkedHashMap<>();
        if (from != null) {
            params.put("from", from.toString());
        }
        if (to != null) {
            params.put("to", to.toString());
        }

        return params;
    }
}
