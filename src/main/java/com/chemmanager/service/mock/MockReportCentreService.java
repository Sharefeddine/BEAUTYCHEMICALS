package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import com.chemmanager.model.analytics.ProductionReport;
import com.chemmanager.model.analytics.PurchaseReport;
import com.chemmanager.model.analytics.ReportCatalogueEntry;
import com.chemmanager.model.analytics.SafetyReport;
import com.chemmanager.model.analytics.SalesReport;
import com.chemmanager.model.analytics.StockReport;
import com.chemmanager.service.ReportCentreService;

/**
 * The report centre of the demo dataset.
 *
 * <p>The figures are the seeded ones the API returns on a fresh database, and every total is summed
 * from the detail lines it heads: the demo therefore reconciles exactly like the server does.</p>
 */
public final class MockReportCentreService implements ReportCentreService {

    private static final BigDecimal VAT = new BigDecimal("19");

    @Override
    public List<ReportCatalogueEntry> catalogue() {
        return List.of(
                new ReportCatalogueEntry("result", "FINANCE", true),
                new ReportCatalogueEntry("margin", "FINANCE", true),
                new ReportCatalogueEntry("cashflow", "FINANCE", true),
                new ReportCatalogueEntry("sales", "COMMERCE", true),
                new ReportCatalogueEntry("purchases", "COMMERCE", true),
                new ReportCatalogueEntry("stock", "OPERATIONS", false),
                new ReportCatalogueEntry("production", "OPERATIONS", true),
                new ReportCatalogueEntry("safety", "SAFETY", false));
    }

    @Override
    public SalesReport sales(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        String[] names = {"Pharmacie El Amel", "Sarl Cosmetique Setif", "Epicerie Benali"};
        BigDecimal[] amounts = {new BigDecimal("412000.00"), new BigDecimal("268500.00"),
                new BigDecimal("94500.00")};
        int[] counts = {14, 9, 6};

        List<SalesReport.Customer> customers = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            BigDecimal ttc = amounts[i].multiply(BigDecimal.ONE.add(VAT.movePointLeft(2)))
                    .setScale(2, RoundingMode.HALF_UP);
            customers.add(new SalesReport.Customer(i + 1, names[i], counts[i], amounts[i], ttc,
                    ttc.multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP)));
        }
        String[] products = {"Savon liquide 1L", "Gel douche 500ml", "Détergent 5L"};
        BigDecimal[] quantities = {new BigDecimal("1450"), new BigDecimal("980"), new BigDecimal("320")};
        BigDecimal[] revenues = {new BigDecimal("319000.00"), new BigDecimal("245000.00"),
                new BigDecimal("211000.00")};
        List<SalesReport.Product> byProduct = new ArrayList<>();
        for (int i = 0; i < products.length; i++) {
            byProduct.add(new SalesReport.Product(i + 1, "PF-000" + (i + 1), products[i], quantities[i],
                    revenues[i]));
        }
        List<SalesReport.Month> months = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        int index = 0;
        while (!cursor.atEndOfMonth().isAfter(end) && index < 6) {
            months.add(new SalesReport.Month(cursor.toString(), counts[index % counts.length],
                    amounts[index % amounts.length], amounts[index % amounts.length]
                            .multiply(BigDecimal.ONE.add(VAT.movePointLeft(2)))
                            .setScale(2, RoundingMode.HALF_UP)));
            cursor = cursor.plusMonths(1);
            index++;
        }

        BigDecimal amountHt = sum(customers.stream().map(SalesReport.Customer::amountHt).toList());
        BigDecimal totalTtc = sum(customers.stream().map(SalesReport.Customer::totalTtc).toList());
        BigDecimal outstanding = sum(customers.stream().map(SalesReport.Customer::outstanding).toList());
        int invoices = customers.stream().mapToInt(SalesReport.Customer::invoiceCount).sum();

        return new SalesReport(start, end,
                new SalesReport.Totals(invoices, amountHt,
                        totalTtc.subtract(amountHt).setScale(2, RoundingMode.HALF_UP), totalTtc,
                        totalTtc.subtract(outstanding).setScale(2, RoundingMode.HALF_UP), outstanding),
                months, customers, byProduct);
    }

    @Override
    public PurchaseReport purchases(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<PurchaseReport.Status> statuses = List.of(
                new PurchaseReport.Status("RECEIVED", 6, new BigDecimal("322200.00")),
                new PurchaseReport.Status("ORDERED", 3, new BigDecimal("144000.00")),
                new PurchaseReport.Status("DRAFT", 1, new BigDecimal("38000.00")));
        List<PurchaseReport.Supplier> suppliers = List.of(
                new PurchaseReport.Supplier(1, "Chimie Sétif", 5, new BigDecimal("288000.00")),
                new PurchaseReport.Supplier(2, "Emballages Maghreb", 3, new BigDecimal("152200.00")),
                new PurchaseReport.Supplier(3, "Parfums Alger", 2, new BigDecimal("64000.00")));
        List<PurchaseReport.Month> months = List.of(
                new PurchaseReport.Month(YearMonth.from(start).toString(), 4, new BigDecimal("210400.00")),
                new PurchaseReport.Month(YearMonth.from(start).plusMonths(1).toString(), 6,
                        new BigDecimal("293800.00")));
        BigDecimal totalTtc = sum(statuses.stream().map(PurchaseReport.Status::totalTtc).toList());
        BigDecimal amountHt = totalTtc.divide(BigDecimal.ONE.add(VAT.movePointLeft(2)), 2, RoundingMode.HALF_UP);

        return new PurchaseReport(start, end,
                new PurchaseReport.Totals(statuses.stream().mapToInt(PurchaseReport.Status::orderCount).sum(),
                        amountHt, totalTtc.subtract(amountHt), totalTtc, 5, new BigDecimal("1840.500")),
                statuses, months, suppliers);
    }

    @Override
    public StockReport stock(int expiryDays) {
        List<StockReport.Warehouse> warehouses = List.of(
                new StockReport.Warehouse(1, "WH-MAT", "Magasin matières", new BigDecimal("0"),
                        new BigDecimal("486300.00"), new BigDecimal("486300.00")),
                new StockReport.Warehouse(2, "WH-PF", "Magasin produits finis",
                        new BigDecimal("712450.00"), new BigDecimal("0"), new BigDecimal("712450.00")));
        List<StockReport.Expiring> expiring = List.of(
                new StockReport.Expiring("Savon liquide 1L", "LOT-2026-0042", "Magasin produits finis",
                        new BigDecimal("140"), LocalDate.now().plusDays(21), 21, "RELEASED"),
                new StockReport.Expiring("Gel douche 500ml", "LOT-2026-0038", "Magasin produits finis",
                        new BigDecimal("64"), LocalDate.now().plusDays(48), 48, "QUARANTINE"));
        List<StockReport.LowStock> lowStock = List.of(
                new StockReport.LowStock("PF-0002", "Gel douche 500ml", new BigDecimal("250"),
                        new BigDecimal("180")),
                new StockReport.LowStock("PF-0003", "Détergent 5L", new BigDecimal("120"),
                        new BigDecimal("96")));

        return new StockReport(new StockReport.Totals(
                sum(warehouses.stream().map(StockReport.Warehouse::totalValue).toList()),
                expiring.size(), 1, 1, lowStock.size()), warehouses, expiring, lowStock);
    }

    @Override
    public ProductionReport production(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<ProductionReport.Product> products = List.of(
                new ProductionReport.Product("PF-0001", "Savon liquide 1L", 4, new BigDecimal("1200"),
                        new BigDecimal("1160"), new BigDecimal("18"), new BigDecimal("214800.00")),
                new ProductionReport.Product("PF-0002", "Gel douche 500ml", 3, new BigDecimal("800"),
                        new BigDecimal("764"), new BigDecimal("12"), new BigDecimal("131200.00")),
                new ProductionReport.Product("PF-0003", "Détergent 5L", 2, new BigDecimal("300"),
                        new BigDecimal("288"), new BigDecimal("6"), new BigDecimal("90400.00")));
        List<ProductionReport.Status> statuses = List.of(
                new ProductionReport.Status("COMPLETED", 7, new BigDecimal("1924")),
                new ProductionReport.Status("IN_PROGRESS", 2, new BigDecimal("288")));
        BigDecimal planned = sum(products.stream().map(ProductionReport.Product::plannedQuantity).toList());
        BigDecimal actual = sum(products.stream().map(ProductionReport.Product::actualQuantity).toList());
        BigDecimal waste = sum(products.stream().map(ProductionReport.Product::wasteQuantity).toList());
        BigDecimal cost = sum(products.stream().map(ProductionReport.Product::totalCost).toList());

        return new ProductionReport(start, end,
                new ProductionReport.Totals(products.stream().mapToInt(ProductionReport.Product::orderCount).sum(),
                        planned, actual, waste, cost,
                        actual.signum() == 0 ? BigDecimal.ZERO
                                : actual.multiply(new BigDecimal("100"))
                                        .divide(planned, 2, RoundingMode.HALF_UP),
                        actual.signum() == 0 ? BigDecimal.ZERO
                                : cost.divide(actual, 2, RoundingMode.HALF_UP)),
                statuses, products);
    }

    @Override
    public SafetyReport safety() {
        List<SafetyReport.Severity> severities = List.of(
                new SafetyReport.Severity("MINOR", "CLOSED", 1),
                new SafetyReport.Severity("MODERATE", "OPEN", 1));
        List<SafetyReport.Type> types = List.of(
                new SafetyReport.Type("SPILL", 1),
                new SafetyReport.Type("EXPOSURE", 1));
        List<SafetyReport.MissingSheet> missing = List.of(
                new SafetyReport.MissingSheet("MP-0007", "Soude caustique en paillettes", "GHS05"),
                new SafetyReport.MissingSheet("MP-0008", "Acide citrique", "GHS07"),
                new SafetyReport.MissingSheet("MP-0009", "Alcool isopropylique", "GHS02"));

        return new SafetyReport(new SafetyReport.Materials(12, 11, missing.size(), 1),
                new SafetyReport.Incidents(severities, types,
                        types.stream().mapToInt(SafetyReport.Type::incidentCount).sum()),
                missing);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
