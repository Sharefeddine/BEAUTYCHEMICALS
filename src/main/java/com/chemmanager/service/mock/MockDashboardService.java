package com.chemmanager.service.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.chemmanager.model.DateRange;
import com.chemmanager.model.Product;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Severity;
import com.chemmanager.model.analytics.CategoryShare;
import com.chemmanager.model.analytics.CustomerPerformance;
import com.chemmanager.model.analytics.DashboardData;
import com.chemmanager.model.analytics.KpiSet;
import com.chemmanager.model.analytics.MonthPoint;
import com.chemmanager.model.analytics.StockAlert;
import com.chemmanager.model.analytics.StockAlertType;
import com.chemmanager.model.analytics.TopProduct;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.repository.MockDataStore.BatchRecord;
import com.chemmanager.repository.MockDataStore.CustomerRecord;
import com.chemmanager.repository.MockDataStore.ExpenseRecord;
import com.chemmanager.repository.MockDataStore.ProductionRecord;
import com.chemmanager.repository.MockDataStore.PurchaseRecord;
import com.chemmanager.repository.MockDataStore.SalesRecord;
import com.chemmanager.service.DashboardService;
import com.chemmanager.util.DateRanges;

/**
 * Computes the executive dashboard from the demo transaction set.
 *
 * <p>Every KPI is an aggregation over {@link MockDataStore} records (sales lines, purchase
 * orders, expenses, production orders, batches), exactly like the SQL aggregations performed
 * by {@code backend/repositories/DashboardRepository.php}. Nothing is hardcoded, so changing
 * the date range or recording new transactions immediately changes the numbers.</p>
 */
public final class MockDashboardService implements DashboardService {

    private static final int EXPIRY_WINDOW_DAYS = 60;
    private static final int TOP_LIST_SIZE = 6;
    private static final int ALERT_LIST_SIZE = 12;
    private static final Set<String> PRODUCTION_EXCLUDED = Set.of("CANCELLED", "REJECTED", "DRAFT");

    private final MockDataStore store = MockDataStore.get();

    @Override
    public DashboardData load(DateRange range) {
        DateRange effective = range == null ? DateRanges.lastMonths(12, LocalDate.now()) : range;
        LocalDate today = LocalDate.now();

        List<SalesRecord> allSales = store.sales();
        List<SalesRecord> rangeSales = allSales.stream().filter(inRange(effective, SalesRecord::date)).toList();
        List<PurchaseRecord> rangePurchases = store.purchases().stream()
                .filter(inRange(effective, PurchaseRecord::date)).toList();
        List<ExpenseRecord> rangeExpenses = store.expenses().stream()
                .filter(inRange(effective, ExpenseRecord::date)).toList();
        List<ProductionRecord> rangeProduction = store.production().stream()
                .filter(inRange(effective, ProductionRecord::date))
                .filter(order -> !"CANCELLED".equals(order.status()))
                .toList();

        BigDecimal revenue = total(rangeSales, SalesRecord::revenue);
        BigDecimal costOfGoods = total(rangeSales, SalesRecord::cost);
        BigDecimal grossProfit = revenue.subtract(costOfGoods).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expenses = total(rangeExpenses, ExpenseRecord::amount);
        BigDecimal netProfit = grossProfit.subtract(expenses).setScale(2, RoundingMode.HALF_UP);

        BigDecimal salesToday = total(allSales.stream().filter(s -> s.date().equals(today)).toList(),
                SalesRecord::revenue);
        BigDecimal salesMonth = total(allSales.stream()
                .filter(s -> DateRange.ofMonth(today).contains(s.date())).toList(), SalesRecord::revenue);
        BigDecimal salesYear = total(allSales.stream()
                .filter(s -> s.date().getYear() == today.getYear()).toList(), SalesRecord::revenue);

        BigDecimal inventoryValue = store.products().stream().map(Product::stockValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(store.rawMaterials().stream().map(RawMaterial::stockValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal receivables = total(allSales.stream().filter(s -> !s.paid()).toList(), SalesRecord::totalTtc);
        BigDecimal payables = total(store.purchases().stream().filter(p -> !p.paid()).toList(),
                PurchaseRecord::totalTtc);
        BigDecimal cashIn = total(allSales.stream().filter(SalesRecord::paid).toList(), SalesRecord::totalTtc);
        BigDecimal cashOut = total(store.purchases().stream().filter(PurchaseRecord::paid).toList(),
                PurchaseRecord::totalTtc)
                .add(total(store.expenses().stream().filter(ExpenseRecord::paid).toList(), ExpenseRecord::amount));
        BigDecimal cashBalance = cashIn.subtract(cashOut).setScale(2, RoundingMode.HALF_UP);

        List<Product> lowStockProducts = store.products().stream().filter(Product::isLowStock).toList();
        List<RawMaterial> lowStockMaterials = store.rawMaterials().stream().filter(RawMaterial::isLowStock).toList();
        int expiringBatches = (int) store.batches().stream()
                .filter(b -> b.expiryDate() != null && !b.expiryDate().isBefore(today)
                        && b.expiryDate().isBefore(today.plusDays(EXPIRY_WINDOW_DAYS)))
                .count();

        int invoiceCount = (int) rangeSales.stream()
                .map(s -> s.date() + "#" + s.customerId()).distinct().count();
        BigDecimal averageBasket = invoiceCount == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(invoiceCount), 2, RoundingMode.HALF_UP);

        KpiSet kpis = new KpiSet(
                salesToday, salesMonth, salesYear, revenue,
                total(rangePurchases, PurchaseRecord::amountHt),
                expenses, grossProfit, netProfit, inventoryValue,
                receivables, payables, cashBalance,
                total(rangeProduction, ProductionRecord::actualQty),
                averageBasket,
                lowStockProducts.size() + lowStockMaterials.size(),
                expiringBatches,
                (int) store.production().stream().filter(ProductionRecord::isOpen).count(),
                (int) allSales.stream().filter(s -> !s.paid()).map(s -> s.date() + "#" + s.customerId()).distinct().count());

        return new DashboardData(
                effective,
                kpis,
                buildMonthlySeries(effective),
                buildCategoryShares(rangeSales),
                buildInventoryDistribution(),
                buildTopProducts(rangeSales, Comparator.comparing(ProductAggregate::revenue).reversed()),
                buildTopProducts(rangeSales, Comparator.comparing(ProductAggregate::profit).reversed()),
                buildCustomerPerformance(rangeSales),
                buildLowStockAlerts(lowStockProducts, lowStockMaterials),
                buildExpiryAlerts(today),
                store.activities().stream().sorted(Comparator.comparing(
                        com.chemmanager.model.analytics.ActivityEntry::occurredAt).reversed()).toList(),
                Instant.now());
    }

    // ------------------------------------------------------------------ charts

    private List<MonthPoint> buildMonthlySeries(DateRange range) {
        Map<YearMonth, BigDecimal> salesByMonth = emptyMonths(range);
        Map<YearMonth, BigDecimal> purchasesByMonth = emptyMonths(range);
        Map<YearMonth, BigDecimal> expensesByMonth = emptyMonths(range);
        Map<YearMonth, BigDecimal> productionCostByMonth = emptyMonths(range);
        Map<YearMonth, BigDecimal> productionQtyByMonth = emptyMonths(range);

        for (SalesRecord sale : store.sales()) {
            YearMonth key = YearMonth.from(sale.date());
            if (salesByMonth.containsKey(key)) {
                salesByMonth.merge(key, sale.revenue(), BigDecimal::add);
            }
        }
        for (PurchaseRecord purchase : store.purchases()) {
            YearMonth key = YearMonth.from(purchase.date());
            if (purchasesByMonth.containsKey(key)) {
                purchasesByMonth.merge(key, purchase.amountHt(), BigDecimal::add);
            }
        }
        for (ExpenseRecord expense : store.expenses()) {
            YearMonth key = YearMonth.from(expense.date());
            if (expensesByMonth.containsKey(key)) {
                expensesByMonth.merge(key, expense.amount(), BigDecimal::add);
            }
        }
        for (ProductionRecord order : store.production()) {
            YearMonth key = YearMonth.from(order.date());
            if (productionCostByMonth.containsKey(key) && !"CANCELLED".equals(order.status())) {
                productionCostByMonth.merge(key, order.totalCost(), BigDecimal::add);
                productionQtyByMonth.merge(key, order.actualQty(), BigDecimal::add);
            }
        }

        List<MonthPoint> points = new ArrayList<>();
        for (LocalDate month : DateRanges.monthStarts(range)) {
            YearMonth key = YearMonth.from(month);
            points.add(new MonthPoint(month,
                    salesByMonth.getOrDefault(key, BigDecimal.ZERO),
                    purchasesByMonth.getOrDefault(key, BigDecimal.ZERO),
                    expensesByMonth.getOrDefault(key, BigDecimal.ZERO),
                    productionCostByMonth.getOrDefault(key, BigDecimal.ZERO),
                    productionQtyByMonth.getOrDefault(key, BigDecimal.ZERO)));
        }
        return points;
    }

    private static Map<YearMonth, BigDecimal> emptyMonths(DateRange range) {
        Map<YearMonth, BigDecimal> months = new LinkedHashMap<>();
        for (LocalDate month : DateRanges.monthStarts(range)) {
            months.put(YearMonth.from(month), BigDecimal.ZERO);
        }
        return months;
    }

    private List<CategoryShare> buildCategoryShares(List<SalesRecord> rangeSales) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (SalesRecord sale : rangeSales) {
            byCategory.merge(sale.category(), sale.revenue(), BigDecimal::add);
        }
        return byCategory.entrySet().stream()
                .map(entry -> new CategoryShare(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryShare::value).reversed())
                .limit(8)
                .toList();
    }

    private List<CategoryShare> buildInventoryDistribution() {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Product product : store.products()) {
            byCategory.merge(product.category(), product.stockValue(), BigDecimal::add);
        }
        for (RawMaterial material : store.rawMaterials()) {
            byCategory.merge(material.category(), material.stockValue(), BigDecimal::add);
        }
        return byCategory.entrySet().stream()
                .map(entry -> new CategoryShare(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategoryShare::value).reversed())
                .toList();
    }

    /** Aggregation holder used for the best-seller / profitability rankings. */
    private record ProductAggregate(String sku, String name, String category, BigDecimal quantity,
                                    BigDecimal revenue, BigDecimal profit) {
    }

    private List<TopProduct> buildTopProducts(List<SalesRecord> rangeSales, Comparator<ProductAggregate> ordering) {
        Map<String, ProductAggregate> aggregates = new LinkedHashMap<>();
        for (SalesRecord sale : rangeSales) {
            aggregates.merge(sale.sku(), new ProductAggregate(sale.sku(), sale.productName(), sale.category(),
                            sale.quantity(), sale.revenue(), sale.revenue().subtract(sale.cost())),
                    (left, right) -> new ProductAggregate(left.sku(), left.name(), left.category(),
                            left.quantity().add(right.quantity()),
                            left.revenue().add(right.revenue()),
                            left.profit().add(right.profit())));
        }
        return aggregates.values().stream()
                .sorted(ordering)
                .limit(TOP_LIST_SIZE)
                .map(aggregate -> new TopProduct(aggregate.sku(), aggregate.name(), aggregate.category(),
                        aggregate.quantity(), aggregate.revenue(), aggregate.profit()))
                .toList();
    }

    private List<CustomerPerformance> buildCustomerPerformance(List<SalesRecord> rangeSales) {
        Map<Long, BigDecimal> revenueByCustomer = new LinkedHashMap<>();
        Map<Long, Set<LocalDate>> orderDays = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        Map<Long, String> wilayas = new LinkedHashMap<>();
        Map<Long, BigDecimal> balances = new LinkedHashMap<>();
        for (CustomerRecord customer : store.customers()) {
            names.put(customer.id(), customer.name());
            wilayas.put(customer.id(), customer.wilaya());
            balances.put(customer.id(), customer.balance());
        }
        for (SalesRecord sale : rangeSales) {
            revenueByCustomer.merge(sale.customerId(), sale.revenue(), BigDecimal::add);
            orderDays.computeIfAbsent(sale.customerId(), key -> new HashSet<>()).add(sale.date());
        }
        return revenueByCustomer.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(8)
                .map(entry -> new CustomerPerformance(
                        names.getOrDefault(entry.getKey(), "—"),
                        wilayas.getOrDefault(entry.getKey(), ""),
                        entry.getValue(),
                        balances.getOrDefault(entry.getKey(), BigDecimal.ZERO),
                        orderDays.getOrDefault(entry.getKey(), Set.of()).size()))
                .toList();
    }

    // ------------------------------------------------------------------ alerts

    private List<StockAlert> buildLowStockAlerts(List<Product> products, List<RawMaterial> materials) {
        List<StockAlert> alerts = new ArrayList<>();
        for (Product product : products) {
            StockAlertType type = product.isOutOfStock() ? StockAlertType.OUT_OF_STOCK : StockAlertType.LOW_STOCK;
            Severity severity = product.isOutOfStock() ? Severity.CRITICAL : Severity.WARNING;
            alerts.add(new StockAlert(product.id(), type, product.sku(), product.displayName(false),
                    product.category(), BigDecimal.valueOf(product.stockOnHand()),
                    BigDecimal.valueOf(product.minStock()), product.unit(), null,
                    "PF - Produits finis", severity));
        }
        for (RawMaterial material : materials) {
            StockAlertType type = material.isOutOfStock() ? StockAlertType.OUT_OF_STOCK : StockAlertType.LOW_STOCK;
            Severity severity = material.isOutOfStock() ? Severity.CRITICAL : Severity.WARNING;
            alerts.add(new StockAlert(material.id() + 10_000, type, material.code(), material.commercialName(),
                    material.category(), material.stock(), material.minStock(), material.storageUnit(), null,
                    "MP - Matières premières", severity));
        }
        return alerts.stream()
                .sorted(Comparator.comparing(StockAlert::severity).reversed())
                .limit(ALERT_LIST_SIZE)
                .toList();
    }

    private List<StockAlert> buildExpiryAlerts(LocalDate today) {
        List<StockAlert> alerts = new ArrayList<>();
        for (BatchRecord batch : store.batches()) {
            if (batch.expiryDate() == null) {
                continue;
            }
            boolean expired = batch.expiryDate().isBefore(today);
            boolean expiringSoon = !expired && batch.expiryDate().isBefore(today.plusDays(EXPIRY_WINDOW_DAYS));
            if (!expired && !expiringSoon) {
                continue;
            }
            Severity severity = expired || batch.expiryDate().isBefore(today.plusDays(15))
                    ? Severity.CRITICAL : Severity.WARNING;
            alerts.add(new StockAlert(alerts.size() + 1,
                    expired ? StockAlertType.EXPIRED : StockAlertType.EXPIRING,
                    batch.itemCode(), batch.itemName(), batch.batchNumber(),
                    batch.quantity(), BigDecimal.ZERO, batch.unit(),
                    batch.expiryDate(), batch.warehouse(), severity));
        }
        return alerts.stream()
                .sorted(Comparator.comparing(StockAlert::expiryDate))
                .limit(ALERT_LIST_SIZE)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private static <T> Predicate<T> inRange(DateRange range, Function<T, LocalDate> dateExtractor) {
        return item -> {
            LocalDate date = dateExtractor.apply(item);
            return date != null && range.contains(date);
        };
    }

    private static <T> BigDecimal total(List<T> items, Function<T, BigDecimal> mapper) {
        return items.stream()
                .map(mapper)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
