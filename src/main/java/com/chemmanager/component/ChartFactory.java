package com.chemmanager.component;

import java.math.BigDecimal;
import java.util.List;

import com.chemmanager.localization.I18n;
import com.chemmanager.model.analytics.CategoryShare;
import com.chemmanager.model.analytics.MonthPoint;
import com.chemmanager.model.analytics.TopProduct;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.Labels;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.util.StringConverter;

/**
 * Builds the themed charts of the dashboard. All series are populated from
 * {@link com.chemmanager.model.analytics.DashboardData}, i.e. from real aggregated transactions.
 */
public final class ChartFactory {

    private ChartFactory() {
    }

    public static BarChart<String, Number> salesVersusPurchases(List<MonthPoint> points, String salesLabel,
                                                                String purchasesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = compactAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setCategoryGap(14);
        chart.setBarGap(3);
        chart.setPrefHeight(280);

        XYChart.Series<String, Number> sales = new XYChart.Series<>();
        sales.setName(salesLabel);
        XYChart.Series<String, Number> purchases = new XYChart.Series<>();
        purchases.setName(purchasesLabel);
        for (MonthPoint point : points) {
            sales.getData().add(new XYChart.Data<>(Formatters.month(point.month()), point.sales()));
            purchases.getData().add(new XYChart.Data<>(Formatters.month(point.month()), point.purchases()));
        }
        chart.getData().addAll(sales, purchases);
        return describe(chart, "chart.a11y.sales_purchases");
    }

    public static LineChart<String, Number> revenueVersusExpenses(List<MonthPoint> points, String revenueLabel,
                                                                  String expensesLabel) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = compactAxis();
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setPrefHeight(280);

        XYChart.Series<String, Number> revenue = new XYChart.Series<>();
        revenue.setName(revenueLabel);
        XYChart.Series<String, Number> expenses = new XYChart.Series<>();
        expenses.setName(expensesLabel);
        for (MonthPoint point : points) {
            revenue.getData().add(new XYChart.Data<>(Formatters.month(point.month()), point.sales()));
            expenses.getData().add(new XYChart.Data<>(Formatters.month(point.month()),
                    point.expenses().add(point.purchases())));
        }
        chart.getData().addAll(revenue, expenses);
        return describe(chart, "chart.a11y.revenue_expenses");
    }

    public static BarChart<String, Number> productionByMonth(List<MonthPoint> points, String label) {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = compactAxis();
        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(10);
        chart.setPrefHeight(260);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(label);
        for (MonthPoint point : points) {
            series.getData().add(new XYChart.Data<>(Formatters.month(point.month()), point.productionQuantity()));
        }
        chart.getData().add(series);
        return describe(chart, "chart.a11y.production");
    }

    public static PieChart categoryPie(List<CategoryShare> shares) {
        PieChart chart = new PieChart();
        chart.setLegendVisible(true);
        chart.setAnimated(false);
        chart.setLabelsVisible(false);
        chart.setPrefHeight(280);
        BigDecimal total = shares.stream().map(CategoryShare::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (CategoryShare share : shares) {
            double percent = total.signum() == 0 ? 0
                    : share.value().doubleValue() * 100 / total.doubleValue();
            PieChart.Data data = new PieChart.Data(
                    Labels.category(share.label()) + " (" + String.format("%.0f%%", percent) + ")",
                    share.value().doubleValue());
            chart.getData().add(data);
        }
        return describe(chart, "chart.a11y.categories");
    }

    public static BarChart<Number, String> horizontalRanking(List<TopProduct> products, String seriesName) {
        NumberAxis xAxis = compactAxis();
        CategoryAxis yAxis = new CategoryAxis();
        BarChart<Number, String> chart = new BarChart<>(xAxis, yAxis);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setBarGap(2);
        chart.setCategoryGap(6);
        chart.setPrefHeight(260);
        chart.setHorizontalGridLinesVisible(false);
        chart.setVerticalGridLinesVisible(true);
        XYChart.Series<Number, String> series = new XYChart.Series<>();
        series.setName(seriesName);
        for (TopProduct product : products) {
            series.getData().add(new XYChart.Data<>(product.revenue(), product.displayName()));
        }
        chart.getData().add(series);
        return describe(chart, "chart.a11y.ranking");
    }

    /**
     * A chart is invisible to a screen reader: it publishes one sentence saying what it plots, so
     * the information is not lost when the picture is (design system section 7). One overload per
     * chart type keeps the concrete type at the call site without a raw {@code Chart}.
     */
    private static <X, Y> BarChart<X, Y> describe(BarChart<X, Y> chart, String key) {
        chart.setAccessibleText(I18n.get().t(key));
        return chart;
    }

    private static <X, Y> LineChart<X, Y> describe(LineChart<X, Y> chart, String key) {
        chart.setAccessibleText(I18n.get().t(key));
        return chart;
    }

    private static PieChart describe(PieChart chart, String key) {
        chart.setAccessibleText(I18n.get().t(key));
        return chart;
    }

    private static NumberAxis compactAxis() {
        NumberAxis axis = new NumberAxis();
        axis.setForceZeroInRange(true);
        axis.setAutoRanging(true);
        axis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number value) {
                if (value == null) {
                    return "";
                }
                double amount = value.doubleValue();
                if (Math.abs(amount) >= 1_000_000) {
                    return String.format("%.1fM", amount / 1_000_000);
                }
                if (Math.abs(amount) >= 1_000) {
                    return String.format("%.0fK", amount / 1_000);
                }
                return String.format("%.0f", amount);
            }

            @Override
            public Number fromString(String text) {
                return 0;
            }
        });
        return axis;
    }
}
