package com.chemmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.chemmanager.model.analytics.ProductionReport;
import com.chemmanager.model.analytics.PurchaseReport;
import com.chemmanager.model.analytics.ReportCatalogueEntry;
import com.chemmanager.model.analytics.SafetyReport;
import com.chemmanager.model.analytics.SalesReport;
import com.chemmanager.model.analytics.StockReport;
import com.chemmanager.service.mock.MockReportCentreService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The report centre of the demo dataset: every total must be the sum of the lines it heads, because
 * that is the contract the API keeps and the screen offers to export.
 */
class ReportCentreServiceMockTest {

    private MockReportCentreService centre;

    @BeforeEach
    void setUp() {
        centre = new MockReportCentreService();
    }

    @Test
    @DisplayName("the catalogue lists the eight reports with their family and cadence")
    void catalogueListsEightReports() {
        List<ReportCatalogueEntry> entries = centre.catalogue();

        assertEquals(8, entries.size());
        assertEquals(List.of("result", "margin", "cashflow", "sales", "purchases", "stock", "production", "safety"),
                entries.stream().map(ReportCatalogueEntry::key).toList());
        assertEquals(List.of("FINANCE", "FINANCE", "FINANCE", "COMMERCE", "COMMERCE", "OPERATIONS",
                "OPERATIONS", "SAFETY"), entries.stream().map(ReportCatalogueEntry::group).toList());
        assertFalse(entries.get(5).periodic(), "the stock report is a snapshot");
        assertFalse(entries.get(7).periodic(), "the safety report is a snapshot");
        assertTrue(entries.get(3).periodic(), "the sales report reads a window");
    }

    @Test
    @DisplayName("the sales report totals its customers, its months and its products")
    void salesReportAddsUp() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 30);
        SalesReport report = centre.sales(from, to);

        assertEquals(from, report.from());
        assertEquals(to, report.to());
        assertEquals(sum(report.byCustomer().stream().map(SalesReport.Customer::amountHt).toList()),
                report.totals().amountHt());
        assertEquals(sum(report.byCustomer().stream().map(SalesReport.Customer::totalTtc).toList()),
                report.totals().totalTtc());
        assertEquals(sum(report.byCustomer().stream().map(SalesReport.Customer::outstanding).toList()),
                report.totals().outstanding());
        assertEquals(report.byCustomer().stream().mapToInt(SalesReport.Customer::invoiceCount).sum(),
                report.totals().invoiceCount());
        assertEquals(0, report.totals().amountHt().add(report.totals().vatAmount())
                .compareTo(report.totals().totalTtc()), "net + VAT is the gross amount");
        assertFalse(report.byMonth().isEmpty(), "the window is broken down by month");
        assertEquals(3, report.byProduct().size());
    }

    @Test
    @DisplayName("the purchases report counts its orders once")
    void purchaseReportAddsUp() {
        PurchaseReport report = centre.purchases(LocalDate.now().minusMonths(2), LocalDate.now());

        assertEquals(sum(report.byStatus().stream().map(PurchaseReport.Status::totalTtc).toList()),
                report.totals().totalTtc());
        assertEquals(report.byStatus().stream().mapToInt(PurchaseReport.Status::orderCount).sum(),
                report.totals().orderCount());
        assertEquals(0, report.totals().amountHt().add(report.totals().vatAmount())
                .compareTo(report.totals().totalTtc()));
        assertEquals(10, report.totals().orderCount());
        assertEquals(3, report.bySupplier().size());
        assertTrue(report.totals().receiptCount() > 0);
    }

    @Test
    @DisplayName("the stock report values every warehouse and sums them")
    void stockReportAddsUp() {
        StockReport report = centre.stock(90);

        assertEquals(sum(report.warehouses().stream().map(StockReport.Warehouse::totalValue).toList()),
                report.totals().totalValue());
        assertEquals(report.expiring().size(), report.totals().expiringCount());
        assertEquals(report.lowStock().size(), report.totals().lowStockCount());
        assertTrue(report.totals().totalValue().signum() > 0);
        for (StockReport.Warehouse warehouse : report.warehouses()) {
            assertEquals(0, warehouse.productValue().add(warehouse.materialValue())
                    .compareTo(warehouse.totalValue()), "a warehouse is worth its products plus its materials");
        }
        for (StockReport.Expiring batch : report.expiring()) {
            assertFalse(batch.batchNumber().isBlank());
            assertTrue(batch.daysLeft() >= 0);
        }
    }

    @Test
    @DisplayName("the production report computes its yield and its unit cost from the lines")
    void productionReportAddsUp() {
        ProductionReport report = centre.production(LocalDate.now().minusMonths(1), LocalDate.now());
        ProductionReport.Totals totals = report.totals();

        assertEquals(sum(report.byProduct().stream().map(ProductionReport.Product::plannedQuantity).toList()),
                totals.plannedQuantity());
        assertEquals(sum(report.byProduct().stream().map(ProductionReport.Product::actualQuantity).toList()),
                totals.actualQuantity());
        assertEquals(sum(report.byProduct().stream().map(ProductionReport.Product::totalCost).toList()),
                totals.totalCost());
        assertEquals(0, new BigDecimal("96.17").compareTo(totals.yieldPct()));
        assertEquals(0, new BigDecimal("197.29").compareTo(totals.unitCost()));
        assertEquals(report.byProduct().stream().mapToInt(ProductionReport.Product::orderCount).sum(),
                totals.orderCount());
    }

    @Test
    @DisplayName("the safety report shows the undocumented substances and the incidents")
    void safetyReportJoinsTheRegister() {
        SafetyReport report = centre.safety();

        assertEquals(12, report.materials().total());
        assertEquals(11, report.materials().hazardous());
        assertEquals(report.missingSheets().size(), report.materials().withoutSheet());
        assertEquals(3, report.missingSheets().size());
        assertEquals("MP-0007", report.missingSheets().get(0).code());
        assertEquals(2, report.incidents().total());
        assertEquals(report.incidents().byType().stream().mapToInt(SafetyReport.Type::incidentCount).sum(),
                report.incidents().total());
        assertFalse(report.incidents().bySeverity().isEmpty());
    }

    @Test
    @DisplayName("a report reads the window it is given")
    void reportsReadTheirWindow() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        SalesReport report = centre.sales(from, to);

        assertEquals(from, report.from());
        assertEquals(to, report.to());
        assertTrue(report.byMonth().stream().allMatch(month -> month.period().compareTo("2026-03") >= 0),
                "no month of the breakdown predates the window");
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
