package com.chemmanager.controller.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.chemmanager.app.AppContext;
import com.chemmanager.component.Card;
import com.chemmanager.component.ChartFactory;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.DateRange;
import com.chemmanager.model.DateRangePreset;
import com.chemmanager.model.Severity;
import com.chemmanager.model.analytics.ActivityEntry;
import com.chemmanager.model.analytics.CustomerPerformance;
import com.chemmanager.model.analytics.DashboardData;
import com.chemmanager.model.analytics.KpiSet;
import com.chemmanager.model.analytics.MonthPoint;
import com.chemmanager.model.analytics.StockAlert;
import com.chemmanager.model.analytics.TopProduct;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.DateRanges;
import com.chemmanager.util.ExcelExporter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;
import com.chemmanager.util.Money;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Executive dashboard.
 *
 * <p>Everything rendered here comes from {@link com.chemmanager.service.DashboardService}
 * (SQL aggregations in API mode, transaction aggregations in demo mode). The date selector
 * re-queries the service; charts, rankings and alerts are rebuilt from the payload.</p>
 */
public final class DashboardController {

    @FXML private ScrollPane pageScroll;
    @FXML private VBox contentRoot;
    @FXML private Label rangeCaption;
    @FXML private Label generatedLabel;
    @FXML private FlowPane kpiGrid;
    @FXML private FlowPane chartGrid;
    @FXML private FlowPane rankingGrid;
    @FXML private FlowPane alertGrid;
    @FXML private FlowPane bottomGrid;
    @FXML private ComboBox<DateRangePreset> rangeSelector;
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private Button applyButton;
    @FXML private Button exportButton;
    @FXML private Button refreshButton;
    @FXML private ProgressIndicator loading;

    private DateRange currentRange;
    private DashboardData lastData;

    @FXML
    private void initialize() {
        rangeSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(DateRangePreset preset) {
                return preset == null ? "" : I18n.get().t(preset.labelKey());
            }

            @Override
            public DateRangePreset fromString(String text) {
                return null;
            }
        });
        rangeSelector.getItems().setAll(DateRangePreset.selectorValues());
        rangeSelector.setValue(DateRangePreset.LAST_12_MONTHS);
        rangeSelector.setOnAction(event -> onPresetChanged());

        applyButton.setGraphic(Icons.glyph(Icons.Icon.FILTER, 12));
        applyButton.setOnAction(event -> load(selectedRange()));
        exportButton.setGraphic(Icons.glyph(Icons.Icon.EXCEL, 12));
        exportButton.setOnAction(event -> exportToExcel());
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> load(currentRange));
        exportButton.setTooltip(new Tooltip(I18n.get().t("dashboard.export_tooltip")));

        currentRange = DateRangePreset.LAST_12_MONTHS.resolve(LocalDate.now());
        syncPickers(currentRange);
        retranslate();
        I18n.get().localeProperty().addListener((observable, oldValue, newValue) -> {
            retranslate();
            if (lastData != null) {
                render(lastData);
            }
        });
        load(currentRange);
    }

    private void retranslate() {
        rangeCaption.setText(I18n.get().t("dashboard.range"));
        applyButton.setText(I18n.get().t("common.apply"));
        exportButton.setText(I18n.get().t("dashboard.export"));
        refreshButton.setText(I18n.get().t("common.refresh"));
        fromPicker.setPromptText(I18n.get().t("dashboard.from"));
        toPicker.setPromptText(I18n.get().t("dashboard.to"));
        if (generatedLabel != null) {
            generatedLabel.setText("");
        }
    }

    private void onPresetChanged() {
        DateRangePreset preset = rangeSelector.getValue();
        if (preset == null) {
            return;
        }
        if (preset == DateRangePreset.CUSTOM) {
            return;
        }
        DateRange range = preset.resolve(LocalDate.now());
        syncPickers(range);
        load(range);
    }

    private void syncPickers(DateRange range) {
        fromPicker.setValue(range.from());
        toPicker.setValue(range.to());
    }

    private DateRange selectedRange() {
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        if (from == null || to == null) {
            return rangeSelector.getValue() == null
                    ? DateRanges.lastMonths(12, LocalDate.now())
                    : rangeSelector.getValue().resolve(LocalDate.now());
        }
        return new DateRange(from, to);
    }

    private void load(DateRange range) {
        DateRange effective = range == null ? DateRanges.lastMonths(12, LocalDate.now()) : range;
        currentRange = effective;
        setBusy(true);
        FxTasks.run(() -> ServiceFactory.dashboard().load(effective),
                data -> {
                    setBusy(false);
                    lastData = data;
                    render(data);
                },
                error -> {
                    setBusy(false);
                    Toasts.error(I18n.get().t("error.loading"));
                    showError(error);
                });
    }

    private void setBusy(boolean busy) {
        loading.setVisible(busy);
        loading.setManaged(busy);
        refreshButton.setDisable(busy);
        applyButton.setDisable(busy);
        exportButton.setDisable(busy || lastData == null);
    }

    // ------------------------------------------------------------------ rendering

    private void render(DashboardData data) {
        renderKpis(data.kpis(), data.range());
        renderCharts(data);
        renderRankings(data);
        renderAlerts(data);
        renderBottom(data);
        generatedLabel.setText(I18n.get().t("dashboard.generated_at",
                Formatters.dateTime(data.generatedAt()), DateRanges.describe(data.range())));
        exportButton.setDisable(false);
    }

    private void renderKpis(KpiSet kpis, DateRange range) {
        kpiGrid.getChildren().setAll(
                new KpiCard(Icons.Icon.MONEY, "kpi-icon-primary")
                        .title(I18n.get().t("dashboard.sales_today"))
                        .value(Money.compact(kpis.salesToday())).tooltip(Money.format(kpis.salesToday()))
                        .hint(I18n.get().t("dashboard.today_hint", Formatters.shortDate(LocalDate.now()))),
                new KpiCard(Icons.Icon.SALES, "kpi-icon-success")
                        .title(I18n.get().t("dashboard.sales_month"))
                        .value(Money.compact(kpis.salesMonth())).tooltip(Money.format(kpis.salesMonth()))
                        .hint(I18n.get().t("dashboard.sales_year_hint", Money.compact(kpis.salesYear()))),
                new KpiCard(Icons.Icon.REPORTS, "kpi-icon-accent")
                        .title(I18n.get().t("dashboard.sales_range"))
                        .value(Money.compact(kpis.salesInRange())).tooltip(Money.format(kpis.salesInRange()))
                        .hint(I18n.get().t("dashboard.avg_basket", Money.format(kpis.averageBasket()))),
                new KpiCard(Icons.Icon.PURCHASING, "kpi-icon-warning")
                        .title(I18n.get().t("dashboard.purchases"))
                        .value(Money.compact(kpis.purchasesInRange())).tooltip(Money.format(kpis.purchasesInRange()))
                        .hint(I18n.get().t("dashboard.payables_hint", Money.compact(kpis.payables()))),
                new KpiCard(Icons.Icon.EXPENSES, "kpi-icon-danger")
                        .title(I18n.get().t("dashboard.expenses"))
                        .value(Money.compact(kpis.expensesInRange())).tooltip(Money.format(kpis.expensesInRange()))
                        .hint(I18n.get().t("dashboard.range_hint")),
                new KpiCard(Icons.Icon.PERCENT, "kpi-icon-success")
                        .title(I18n.get().t("dashboard.gross_profit"))
                        .value(Money.compact(kpis.grossProfitInRange())).tooltip(Money.format(kpis.grossProfitInRange()))
                        .trend(I18n.get().t("dashboard.margin", kpis.marginPercent().toPlainString()), true),
                new KpiCard(Icons.Icon.SCALE, "kpi-icon-teal")
                        .title(I18n.get().t("dashboard.net_profit"))
                        .value(Money.compact(kpis.netProfitInRange())).tooltip(Money.format(kpis.netProfitInRange()))
                        .hint(I18n.get().t("dashboard.after_expenses")),
                new KpiCard(Icons.Icon.CUBE, "kpi-icon-info")
                        .title(I18n.get().t("dashboard.inventory_value"))
                        .value(Money.compact(kpis.inventoryValue())).tooltip(Money.format(kpis.inventoryValue()))
                        .hint(I18n.get().t("dashboard.low_stock_hint", kpis.lowStockCount())),
                new KpiCard(Icons.Icon.SALES, "kpi-icon-warning")
                        .title(I18n.get().t("dashboard.receivables"))
                        .value(Money.compact(kpis.receivables())).tooltip(Money.format(kpis.receivables()))
                        .hint(I18n.get().t("dashboard.open_orders", kpis.openOrders())),
                new KpiCard(Icons.Icon.MONEY, "kpi-icon-primary")
                        .title(I18n.get().t("dashboard.cash_position"))
                        .value(Money.compact(kpis.cashBalance())).tooltip(Money.format(kpis.cashBalance()))
                        .hint(I18n.get().t("dashboard.cash_hint", Money.compact(kpis.payables()))),
                new KpiCard(Icons.Icon.PRODUCTION, "kpi-icon-accent")
                        .title(I18n.get().t("dashboard.production_range"))
                        .value(Formatters.quantity(kpis.productionQuantityInRange(), "PCE"))
                        .hint(I18n.get().t("dashboard.pending_production", kpis.pendingProductionCount())),
                new KpiCard(Icons.Icon.CLOCK, "kpi-icon-danger")
                        .title(I18n.get().t("dashboard.expiring"))
                        .value(Integer.toString(kpis.expiringCount()))
                        .hint(I18n.get().t("dashboard.expiring_hint")),
                new KpiCard(Icons.Icon.REPORTS, "kpi-icon-primary")
                        .title(I18n.get().t("dashboard.period"))
                        .value(DateRanges.describe(range))
                        .hint(I18n.get().t("dashboard.days_count", range.days())));
    }

    private void renderCharts(DashboardData data) {
        chartGrid.getChildren().setAll(
                chartCard("dashboard.chart.sales_vs_purchases", "dashboard.chart.sales_vs_purchases.sub",
                        ChartFactory.salesVersusPurchases(data.monthly(),
                                I18n.get().t("dashboard.sales"), I18n.get().t("dashboard.purchases"))),
                chartCard("dashboard.chart.revenue_vs_expenses", "dashboard.chart.revenue_vs_expenses.sub",
                        ChartFactory.revenueVersusExpenses(data.monthly(),
                                I18n.get().t("dashboard.revenue"), I18n.get().t("dashboard.total_costs"))),
                chartCard("dashboard.chart.categories", "dashboard.chart.categories.sub",
                        ChartFactory.categoryPie(data.salesByCategory())),
                chartCard("dashboard.chart.production", "dashboard.chart.production.sub",
                        ChartFactory.productionByMonth(data.monthly(), I18n.get().t("production.quantity"))));
    }

    private Card chartCard(String titleKey, String subtitleKey, Node chart) {
        Card card = new Card(I18n.get().t(titleKey), I18n.get().t(subtitleKey));
        card.setPrefWidth(548);
        card.body(chart);
        return card;
    }

    private void renderRankings(DashboardData data) {
        Card bestSellers = new Card(I18n.get().t("dashboard.best_sellers"),
                I18n.get().t("dashboard.best_sellers.sub"));
        bestSellers.setPrefWidth(400);
        bestSellers.body(rankingList(data.bestSellers(), true));

        Card profitable = new Card(I18n.get().t("dashboard.most_profitable"),
                I18n.get().t("dashboard.most_profitable.sub"));
        profitable.setPrefWidth(400);
        profitable.body(rankingList(data.mostProfitable(), false));

        Card customers = new Card(I18n.get().t("dashboard.top_customers"),
                I18n.get().t("dashboard.top_customers.sub"));
        customers.setPrefWidth(400);
        customers.body(customerList(data.topCustomers()));

        rankingGrid.getChildren().setAll(bestSellers, profitable, customers);
    }

    private VBox rankingList(List<TopProduct> products, boolean byRevenue) {
        VBox list = new VBox(2);
        if (products.isEmpty()) {
            list.getChildren().add(emptyLabel());
            return list;
        }
        BigDecimal max = products.stream()
                .map(byRevenue ? TopProduct::revenue : TopProduct::profit)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        int rank = 1;
        for (TopProduct product : products) {
            BigDecimal value = byRevenue ? product.revenue() : product.profit();
            double ratio = max.signum() == 0 ? 0 : value.doubleValue() / max.doubleValue();

            Label rankLabel = new Label(String.valueOf(rank++));
            rankLabel.getStyleClass().addAll("badge", rank <= 3 ? "badge-primary" : "badge-neutral");
            rankLabel.setMinWidth(22);

            Label name = new Label(product.displayName());
            name.getStyleClass().add("notification-title");
            name.setMaxWidth(210);
            Label meta = new Label(Labels.category(product.category()) + " · "
                    + Formatters.quantity(product.quantity(), "PCE"));
            meta.getStyleClass().add("text-muted");
            meta.setStyle("-fx-font-size: 10.8px;");
            VBox text = new VBox(1, name, meta);
            HBox.setHgrow(text, Priority.ALWAYS);

            Label amount = new Label(Money.compact(value));
            amount.getStyleClass().add("text-bold");
            amount.setStyle("-fx-font-size: 11.8px;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(9, rankLabel, text, spacer, amount);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 8 6 8 6;");
            row.setOnMouseClicked(event -> AppContext.get().navigate(NavigationModule.PRODUCTS));
            Tooltip.install(row, new Tooltip(product.name() + "\n" + Money.format(value)));
            list.getChildren().add(row);
            list.getChildren().add(progressBar(ratio, ratio > 0.66 ? "ok" : ratio > 0.33 ? "warn" : "danger"));
        }
        return list;
    }

    private VBox customerList(List<CustomerPerformance> customers) {
        VBox list = new VBox(2);
        if (customers.isEmpty()) {
            list.getChildren().add(emptyLabel());
            return list;
        }
        BigDecimal max = customers.stream().map(CustomerPerformance::revenue)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        for (CustomerPerformance customer : customers) {
            Label name = new Label(customer.customer());
            name.getStyleClass().add("notification-title");
            name.setMaxWidth(220);
            Label meta = new Label(customer.wilaya() + " · " + I18n.get().t("dashboard.orders",
                    customer.orders()) + " · " + I18n.get().t("dashboard.balance_short",
                    Money.compact(customer.balance())));
            meta.getStyleClass().add("text-muted");
            meta.setStyle("-fx-font-size: 10.8px;");
            VBox text = new VBox(1, name, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            Label amount = new Label(Money.compact(customer.revenue()));
            amount.getStyleClass().add("text-bold");
            amount.setStyle("-fx-font-size: 11.8px;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(9, text, spacer, amount);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 8 6 8 6;");
            row.setOnMouseClicked(event -> AppContext.get().navigate(NavigationModule.CUSTOMERS));
            list.getChildren().add(row);
            double ratio = max.signum() == 0 ? 0 : customer.revenue().doubleValue() / max.doubleValue();
            list.getChildren().add(progressBar(ratio, "ok"));
        }
        return list;
    }

    private void renderAlerts(DashboardData data) {
        Card lowStock = new Card(I18n.get().t("dashboard.alerts.low_stock"),
                I18n.get().t("dashboard.alerts.low_stock.sub"));
        lowStock.setPrefWidth(548);
        lowStock.body(alertList(data.lowStockAlerts(), true));

        Card expiry = new Card(I18n.get().t("dashboard.alerts.expiry"),
                I18n.get().t("dashboard.alerts.expiry.sub"));
        expiry.setPrefWidth(548);
        expiry.body(alertList(data.expiryAlerts(), false));

        alertGrid.getChildren().setAll(lowStock, expiry);
    }

    private VBox alertList(List<StockAlert> alerts, boolean stock) {
        VBox list = new VBox(2);
        if (alerts.isEmpty()) {
            list.getChildren().add(emptyLabel());
            return list;
        }
        for (StockAlert alert : alerts) {
            Label icon = new Label();
            icon.setGraphic(Icons.glyph(stock ? Icons.Icon.CUBE : Icons.Icon.CLOCK, 12));
            icon.setStyle("-fx-padding: 8; -fx-background-radius: 9; -fx-background-color: "
                    + (alert.severity() == Severity.CRITICAL ? "-c-danger-soft;" : "-c-warning-soft;"));

            Label title = new Label(alert.itemName());
            title.getStyleClass().add("notification-title");
            title.setMaxWidth(260);
            Label meta = new Label(alert.itemCode() + " · "
                    + (stock
                    ? I18n.get().t("dashboard.alert.stock_meta",
                    Formatters.decimal(alert.quantity(), 0), alert.unit(),
                    Formatters.decimal(alert.threshold(), 0))
                    : I18n.get().t("dashboard.alert.expiry_meta",
                    Formatters.shortDate(alert.expiryDate()))));
            meta.getStyleClass().add("text-muted");
            meta.setStyle("-fx-font-size: 10.8px;");
            VBox text = new VBox(1, title, meta);
            HBox.setHgrow(text, Priority.ALWAYS);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, icon, text, spacer, StatusBadge.alert(alert.type()));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("alert-row");
            boolean rawMaterial = alert.itemCode() != null && alert.itemCode().startsWith("MP-");
            row.setOnMouseClicked(event -> AppContext.get().navigate(
                    rawMaterial ? NavigationModule.RAW_MATERIALS : NavigationModule.PRODUCTS));
            list.getChildren().add(row);
        }
        return list;
    }

    private void renderBottom(DashboardData data) {
        Card activities = new Card(I18n.get().t("dashboard.recent_activity"),
                I18n.get().t("dashboard.recent_activity.sub"));
        activities.setPrefWidth(720);
        activities.body(activityList(data.recentActivities()));

        Card health = new Card(I18n.get().t("dashboard.operations"), I18n.get().t("dashboard.operations.sub"));
        health.setPrefWidth(376);
        health.body(operationsList(data));

        bottomGrid.getChildren().setAll(activities, health);
    }

    private VBox activityList(List<ActivityEntry> activities) {
        VBox list = new VBox(0);
        if (activities.isEmpty()) {
            list.getChildren().add(emptyLabel());
            return list;
        }
        for (ActivityEntry entry : activities) {
            Region dot = new Region();
            dot.getStyleClass().addAll("activity-dot", entry.severity().name().toLowerCase());
            Label user = new Label(entry.username());
            user.getStyleClass().add("notification-title");
            Label action = new Label(entry.action());
            action.getStyleClass().add("notification-message");
            action.setWrapText(true);
            Label meta = new Label(Labels.nav(entry.module().toLowerCase().replace('_', '-'))
                    + " · " + entry.reference() + " · " + Formatters.relative(entry.occurredAt()));
            meta.getStyleClass().add("notification-time");

            VBox text = new VBox(1, new HBox(6, user, meta), action);
            HBox.setHgrow(text, Priority.ALWAYS);

            HBox row = new HBox(10, dot, text);
            row.getStyleClass().add("notification-row");
            row.setAlignment(Pos.CENTER_LEFT);
            severityTint(row, entry.severity());
            list.getChildren().add(row);
        }
        return list;
    }

    private void severityTint(HBox row, Severity severity) {
        if (severity == Severity.CRITICAL) {
            row.setStyle("-fx-border-color: transparent transparent -c-border-subtle -c-danger; -fx-border-width: 0 0 1 2;");
        }
    }

    private VBox operationsList(DashboardData data) {
        KpiSet kpis = data.kpis();
        VBox list = new VBox(10);
        list.getChildren().addAll(
                operationRow(Icons.Icon.MONEY, "dashboard.op.cash", Money.format(kpis.cashBalance()), "kpi-icon-primary"),
                operationRow(Icons.Icon.SALES, "dashboard.op.receivables", Money.format(kpis.receivables()), "kpi-icon-warning"),
                operationRow(Icons.Icon.PURCHASING, "dashboard.op.payables", Money.format(kpis.payables()), "kpi-icon-danger"),
                operationRow(Icons.Icon.CUBE, "dashboard.op.inventory", Money.format(kpis.inventoryValue()), "kpi-icon-info"),
                operationRow(Icons.Icon.PRODUCTION, "dashboard.op.production",
                        Formatters.quantity(kpis.productionQuantityInRange(), "PCE"), "kpi-icon-accent"),
                operationRow(Icons.Icon.CLOCK, "dashboard.op.expiring",
                        String.valueOf(kpis.expiringCount()), "kpi-icon-danger"),
                operationRow(Icons.Icon.TASKS, "dashboard.op.pending",
                        String.valueOf(kpis.pendingProductionCount()), "kpi-icon-teal"));
        MonthPoint lastMonth = data.monthly().isEmpty() ? null : data.monthly().get(data.monthly().size() - 1);
        if (lastMonth != null) {
            Label trend = new Label(I18n.get().t("dashboard.op.last_month", Formatters.monthLong(lastMonth.month()),
                    Money.compact(lastMonth.sales()), Money.compact(lastMonth.expenses())));
            trend.getStyleClass().add("text-muted");
            trend.setWrapText(true);
            trend.setStyle("-fx-font-size: 10.9px; -fx-padding: 6 2 0 2;");
            list.getChildren().add(trend);
        }
        return list;
    }

    private HBox operationRow(Icons.Icon icon, String labelKey, String value, String accent) {
        HBox row = new HBox(11, Icons.badge(icon, accent, 12));
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(I18n.get().t(labelKey));
        label.getStyleClass().add("text-muted");
        label.setStyle("-fx-font-size: 12px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label amount = new Label(value);
        amount.getStyleClass().add("text-bold");
        row.getChildren().addAll(label, spacer, amount);
        return row;
    }

    private ProgressBar progressBar(double ratio, String styleClass) {
        ProgressBar bar = new ProgressBar(Math.max(0, Math.min(1, ratio)));
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(5);
        bar.setStyle("-fx-padding: 0 0 8 0;");
        bar.getStyleClass().add("mini-bar");
        return bar;
    }

    private Label emptyLabel() {
        Label label = new Label(I18n.get().t("table.empty"));
        label.getStyleClass().add("text-muted");
        label.setStyle("-fx-padding: 18;");
        return label;
    }

    private void showError(Throwable error) {
        String message = error.getMessage() == null ? I18n.get().t("error.unknown") : error.getMessage();
        Dialogs.error(AppContext.get().stage(), I18n.get().t("error.title"), message);
    }

    // ------------------------------------------------------------------ export

    private void exportToExcel() {
        if (lastData == null) {
            Toasts.warning(I18n.get().t("dashboard.export_empty"));
            return;
        }
        String suggested = "chemmanager-dashboard-" + LocalDate.now() + ".xlsx";
        Optional<Path> target = Dialogs.chooseSaveFile(AppContext.get().stage(),
                I18n.get().t("dashboard.export"), suggested, I18n.get().t("dashboard.export_filter"), "xlsx");
        if (target.isEmpty()) {
            return;
        }
        DashboardData snapshot = lastData;
        FxTasks.runVoid(() -> ExcelExporter.exportDashboard(target.get(), snapshot),
                () -> Toasts.success(I18n.get().t("dashboard.export_done", target.get().getFileName().toString())),
                error -> Toasts.error(I18n.get().t("dashboard.export_failed", String.valueOf(error.getMessage()))));
    }

    /** Rounding helper kept for the average-basket style values. */
    private static BigDecimal rounded(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
