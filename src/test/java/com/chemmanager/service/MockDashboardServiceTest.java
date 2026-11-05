package com.chemmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import com.chemmanager.model.DateRange;
import com.chemmanager.model.Product;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.analytics.DashboardData;
import com.chemmanager.model.analytics.MonthPoint;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.mock.MockDashboardService;
import com.chemmanager.util.DateRanges;
import org.junit.jupiter.api.Test;

/**
 * Proves that dashboard figures are aggregations of the stored transactions and not
 * hardcoded constants: the KPI must equal the sum recomputed from the dataset, and must
 * react to the selected range.
 */
class MockDashboardServiceTest {

    private final DashboardService service = new MockDashboardService();
    private final MockDataStore store = MockDataStore.get();

    private BigDecimal expectedSales(DateRange range) {
        return store.sales().stream()
                .filter(record -> range.contains(record.date()))
                .map(MockDataStore.SalesRecord::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Test
    void salesKpiMatchesTheDataset() {
        DateRange range = DateRanges.lastMonths(3, LocalDate.now());
        DashboardData data = service.load(range);
        assertNotNull(data.kpis());
        assertEquals(expectedSales(range), data.kpis().salesInRange().setScale(2, RoundingMode.HALF_UP));
        assertNotNull(data.generatedAt());
    }

    @Test
    void changingTheRangeChangesTheNumbers() {
        DashboardData year = service.load(DateRanges.lastMonths(12, LocalDate.now()));
        DashboardData week = service.load(DateRanges.lastDays(7, LocalDate.now()));
        assertTrue(year.kpis().salesInRange().compareTo(week.kpis().salesInRange()) > 0,
                "a full year cannot sell less than a single week");
        assertFalse(year.monthly().isEmpty());
    }

    @Test
    void chartsAndAlertListsArePopulatedForTheDemoDataset() {
        DashboardData data = service.load(DateRanges.lastMonths(6, LocalDate.now()));
        assertFalse(data.monthly().isEmpty(), "monthly series drive the trend charts");
        for (MonthPoint point : data.monthly()) {
            assertNotNull(point.month());
            assertTrue(point.sales().signum() >= 0);
        }
        assertFalse(data.salesByCategory().isEmpty());
        assertFalse(data.bestSellers().isEmpty());
        assertFalse(data.mostProfitable().isEmpty());
        assertFalse(data.topCustomers().isEmpty());
        assertFalse(data.recentActivities().isEmpty());
        assertTrue(data.kpis().inventoryValue().signum() > 0);
        int lowStock = (int) store.products().stream().filter(Product::isLowStock).count()
                + (int) store.rawMaterials().stream().filter(RawMaterial::isLowStock).count();
        assertEquals(lowStock, data.kpis().lowStockCount(),
                "the low-stock counter must be derived from the catalogue, not hardcoded");
    }

    @Test
    void netProfitIsGrossProfitMinusOperatingExpenses() {
        DashboardData data = service.load(DateRanges.lastMonths(12, LocalDate.now()));
        BigDecimal expected = data.kpis().grossProfitInRange()
                .subtract(data.kpis().expensesInRange())
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(expected, data.kpis().netProfitInRange().setScale(2, RoundingMode.HALF_UP));
    }
}
