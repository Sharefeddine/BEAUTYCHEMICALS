package com.chemmanager.controller.reports;

import java.time.LocalDate;
import java.time.YearMonth;

import com.chemmanager.component.Card;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.Toasts;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.AccountingPeriod;
import com.chemmanager.model.finance.CashflowReport;
import com.chemmanager.model.finance.IncomeStatement;
import com.chemmanager.model.finance.MarginReport;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The three statements the accountant signs: margin by product, cash flow, and the result of a
 * month. Each one is computed by the server from the issued documents and the cash book.
 */
public final class ReportsController {

    @FXML private HBox kpiRow;
    @FXML private VBox marginHost;
    @FXML private VBox cashflowHost;
    @FXML private VBox resultHost;
    @FXML private Label summaryLabel;
    @FXML private DatePicker fromPicker;
    @FXML private DatePicker toPicker;
    @FXML private ComboBox<String> periodSelector;
    @FXML private Button refreshButton;

    @FXML
    private void initialize() {
        YearMonth current = YearMonth.now();
        fromPicker.setValue(LocalDate.of(current.getYear(), 1, 1));
        toPicker.setValue(LocalDate.now());
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> reload());
        fromPicker.setOnAction(event -> reload());
        toPicker.setOnAction(event -> reload());
        periodSelector.setOnAction(event -> reload());

        periodSelector.setItems(FXCollections.observableArrayList(current.toString(),
                current.minusMonths(1).toString()));
        periodSelector.setValue(current.toString());

        FxTasks.run(() -> ServiceFactory.finance().periods(), periods -> {
            java.util.List<String> codes = periods.stream().map(AccountingPeriod::code).toList();
            if (!codes.isEmpty()) {
                periodSelector.setItems(FXCollections.observableArrayList(codes));
                String chosen = codes.contains(current.toString()) ? current.toString()
                        : codes.get(codes.size() - 1);
                periodSelector.setValue(chosen);
            }
            reload();
        }, error -> {
            Toasts.error(error.getMessage());
            reload();
        });
    }

    private void reload() {
        LocalDate from = fromPicker.getValue() == null ? LocalDate.now().withDayOfMonth(1) : fromPicker.getValue();
        LocalDate to = toPicker.getValue() == null ? LocalDate.now() : toPicker.getValue();
        String period = periodSelector.getValue() == null ? YearMonth.now().toString() : periodSelector.getValue();

        FxTasks.run(() -> ServiceFactory.finance().margin(from, to), this::renderMargin,
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().cashflow(from, to), this::renderCashflow,
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().result(period), this::renderResult,
                error -> Toasts.error(error.getMessage()));
    }

    private void renderMargin(MarginReport report) {
        Card card = new Card(I18n.get().t("reports.margin.title"),
                I18n.get().t("reports.margin.subtitle", report.lines().size()));
        VBox rows = new VBox(4);
        for (MarginReport.Line line : report.lines()) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Label name = new Label(line.name());
            name.setMinWidth(260);
            Label quantity = new Label(Formatters.number(line.quantity()));
            quantity.setMinWidth(90);
            Label revenue = new Label(Formatters.currency(line.revenue()));
            revenue.setMinWidth(130);
            Label cost = new Label(Formatters.currency(line.cost()));
            cost.setMinWidth(130);
            Label margin = new Label(Formatters.currency(line.margin()));
            margin.setMinWidth(130);
            Label pct = new Label(Formatters.percent(line.marginPct()));
            pct.setMinWidth(70);
            row.getChildren().addAll(name, quantity, revenue, cost, margin, pct);
            row.getStyleClass().add("text-muted");
            rows.getChildren().add(row);
        }
        if (rows.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("reports.margin.empty"));
            empty.getStyleClass().add("text-muted");
            rows.getChildren().add(empty);
        }
        Label totalLabel = new Label(I18n.get().t("reports.margin.total",
                Formatters.currency(report.revenue()), Formatters.currency(report.cost()),
                Formatters.currency(report.margin()), Formatters.percent(report.marginPct())));
        totalLabel.getStyleClass().add("h3");
        card.body(totalLabel, rows);
        marginHost.getChildren().setAll(card);
        summaryLabel.setText(I18n.get().t("reports.summary", report.from() == null ? "—"
                : Formatters.shortDate(report.from()), report.to() == null ? "—"
                : Formatters.shortDate(report.to())));
    }

    private void renderCashflow(CashflowReport report) {
        Card card = new Card(I18n.get().t("reports.cashflow.title"),
                I18n.get().t("reports.cashflow.subtitle"));
        VBox rows = new VBox(4);
        for (CashflowReport.Month month : report.timeline()) {
            rows.getChildren().add(line(I18n.get().t("reports.cashflow.period") + " " + month.period(),
                    Formatters.currency(month.totalIn()), Formatters.currency(month.totalOut()),
                    Formatters.currency(month.net())));
        }
        for (CashflowReport.Source source : report.bySource()) {
            rows.getChildren().add(line(source.sourceType(),
                    Formatters.currency(source.totalIn()), Formatters.currency(source.totalOut()),
                    Formatters.currency(source.net())));
        }
        if (rows.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("reports.cashflow.empty"));
            empty.getStyleClass().add("text-muted");
            rows.getChildren().add(empty);
        }
        Label totals = new Label(I18n.get().t("reports.cashflow.totals",
                Formatters.currency(report.totalIn()), Formatters.currency(report.totalOut()),
                Formatters.currency(report.net())));
        totals.getStyleClass().add("h3");
        card.body(totals, rows);
        cashflowHost.getChildren().setAll(card);
    }

    private HBox line(String label, String in, String out, String net) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label name = new Label(label);
        name.setMinWidth(200);
        Label inflow = new Label(in);
        inflow.setMinWidth(140);
        Label outflow = new Label(out);
        outflow.setMinWidth(140);
        Label netLabel = new Label(net);
        netLabel.setMinWidth(140);
        row.getChildren().addAll(name, inflow, outflow, netLabel);
        row.getStyleClass().add("text-muted");

        return row;
    }

    private void renderResult(IncomeStatement statement) {
        Card card = new Card(I18n.get().t("reports.result.title", statement.period()),
                I18n.get().t("reports.result.subtitle"));
        VBox facts = new VBox(5,
                fact("reports.result.invoiced", Formatters.currency(statement.invoicedHt())),
                fact("reports.result.credits", Formatters.currency(statement.creditNotesHt())),
                fact("reports.result.revenue", Formatters.currency(statement.revenue())),
                fact("reports.result.cost", Formatters.currency(statement.costOfGoods())),
                fact("reports.result.gross", Formatters.currency(statement.grossMargin())),
                fact("reports.result.expenses", Formatters.currency(statement.expenses())),
                fact("reports.result.result", Formatters.currency(statement.result())));

        VBox categories = new VBox(3);
        for (IncomeStatement.CategoryExpense row : statement.byCategory()) {
            Label line = new Label(row.name() + " · " + Formatters.currency(row.amount()));
            line.getStyleClass().add("text-muted");
            categories.getChildren().add(line);
        }
        if (categories.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("reports.result.no_expense"));
            empty.getStyleClass().add("text-muted");
            categories.getChildren().add(empty);
        }
        Label categoriesTitle = new Label(I18n.get().t("reports.result.by_category"));
        categoriesTitle.getStyleClass().add("h3");
        card.body(facts, categoriesTitle, categories);
        resultHost.getChildren().setAll(card);

        setKpis(statement);
    }

    private void setKpis(IncomeStatement statement) {
        kpiRow.getChildren().setAll(
                new KpiCard(Icons.Icon.SALES, "accent-primary").title(I18n.get().t("reports.kpi.revenue"))
                        .value(Formatters.currency(statement.revenue()))
                        .hint(I18n.get().t("reports.kpi.revenue_hint")),
                new KpiCard(Icons.Icon.REPORTS, "accent-success").title(I18n.get().t("reports.kpi.margin"))
                        .value(Formatters.currency(statement.grossMargin()))
                        .hint(Formatters.percent(statement.grossMarginPct())),
                new KpiCard(Icons.Icon.EXPENSES, "accent-warning").title(I18n.get().t("reports.kpi.expenses"))
                        .value(Formatters.currency(statement.expenses()))
                        .hint(I18n.get().t("reports.kpi.expenses_hint")),
                new KpiCard(Icons.Icon.FINANCE, "accent-danger").title(I18n.get().t("reports.kpi.result"))
                        .value(Formatters.currency(statement.result()))
                        .hint(Formatters.percent(statement.resultPct())));
    }

    private Label fact(String key, String value) {
        Label label = new Label(I18n.get().t(key) + " : " + value);
        label.getStyleClass().add("text-muted");

        return label;
    }
}
