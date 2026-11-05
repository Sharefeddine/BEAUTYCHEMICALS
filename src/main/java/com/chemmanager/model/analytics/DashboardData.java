package com.chemmanager.model.analytics;

import java.time.Instant;
import java.util.List;

import com.chemmanager.model.DateRange;

/** Complete payload backing the dashboard: KPIs, chart series, alert lists and activity. */
public record DashboardData(
        DateRange range,
        KpiSet kpis,
        List<MonthPoint> monthly,
        List<CategoryShare> salesByCategory,
        List<CategoryShare> inventoryDistribution,
        List<TopProduct> bestSellers,
        List<TopProduct> mostProfitable,
        List<CustomerPerformance> topCustomers,
        List<StockAlert> lowStockAlerts,
        List<StockAlert> expiryAlerts,
        List<ActivityEntry> recentActivities,
        Instant generatedAt) {

    public DashboardData {
        monthly = monthly == null ? List.of() : List.copyOf(monthly);
        salesByCategory = salesByCategory == null ? List.of() : List.copyOf(salesByCategory);
        inventoryDistribution = inventoryDistribution == null ? List.of() : List.copyOf(inventoryDistribution);
        bestSellers = bestSellers == null ? List.of() : List.copyOf(bestSellers);
        mostProfitable = mostProfitable == null ? List.of() : List.copyOf(mostProfitable);
        topCustomers = topCustomers == null ? List.of() : List.copyOf(topCustomers);
        lowStockAlerts = lowStockAlerts == null ? List.of() : List.copyOf(lowStockAlerts);
        expiryAlerts = expiryAlerts == null ? List.of() : List.copyOf(expiryAlerts);
        recentActivities = recentActivities == null ? List.of() : List.copyOf(recentActivities);
    }
}
