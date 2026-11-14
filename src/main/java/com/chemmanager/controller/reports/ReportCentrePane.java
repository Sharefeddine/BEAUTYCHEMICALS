package com.chemmanager.controller.reports;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.context.AppContext;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.analytics.ProductionReport;
import com.chemmanager.model.analytics.PurchaseReport;
import com.chemmanager.model.analytics.ReportCatalogueEntry;
import com.chemmanager.model.analytics.SafetyReport;
import com.chemmanager.model.analytics.SalesReport;
import com.chemmanager.model.analytics.StockReport;
import com.chemmanager.service.ReportCentreService;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.ExcelExporter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The report centre: a card per report, and the selected report rendered below it.
 *
 * <p>Every report is read from the server for the window the screen shows; the export button writes
 * the main table of the report to an Excel workbook, so what the accountant sees is what the file
 * contains.</p>
 */
public final class ReportCentrePane {

    private final FlowPane catalogueHost;
    private final VBox reportHost;
    private final DatePicker fromPicker;
    private final DatePicker toPicker;

    private List<ReportCatalogueEntry> catalogue = List.of();
    private String selected = "sales";
    private String sheetName = "";
    private List<String> exportHeaders = List.of();
    private List<List<Object>> exportRows = List.of();
    private Button exportButton;

    public ReportCentrePane(FlowPane catalogueHost, VBox reportHost, DatePicker fromPicker,
                            DatePicker toPicker) {
        this.catalogueHost = catalogueHost;
        this.reportHost = reportHost;
        this.fromPicker = fromPicker;
        this.toPicker = toPicker;
    }

    /** Reads the catalogue and shows the default report. */
    public void reload() {
        FxTasks.run(() -> ServiceFactory.reportCentre().catalogue(), entries -> {
            catalogue = entries == null ? List.of() : entries;
            renderCatalogue();
            show(catalogue.stream().map(ReportCatalogueEntry::key).filter(selected::equals).findFirst()
                    .orElse(catalogue.isEmpty() ? "" : catalogue.get(0).key()));
        }, error -> {
            Toasts.error(error.getMessage());
            catalogueHost.getChildren().setAll(placeholder());
        });
    }

    /** The report the screen is showing, so a window change re-reads that one. */
    public String periodicReport() {
        return selected;
    }

    /** Writes the table of the report on screen to an Excel workbook. */
    public void exportCurrent() {
        export();
    }

    /** Loads and renders one report. */
    public void show(String key) {
        selected = key;
        for (javafx.scene.Node node : catalogueHost.getChildren()) {
            node.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"),
                    key.equals(node.getUserData()));
        }
        if (key == null || key.isBlank()) {
            reportHost.getChildren().setAll(placeholder());
            return;
        }
        LocalDate from = fromPicker.getValue();
        LocalDate to = toPicker.getValue();
        ReportCentreService service = ServiceFactory.reportCentre();

        FxTasks.run(() -> switch (key) {
            case "sales" -> sales(service.sales(from, to));
            case "purchases" -> purchases(service.purchases(from, to));
            case "stock" -> stock(service.stock(90));
            case "production" -> production(service.production(from, to));
            case "safety" -> safety(service.safety());
            default -> null;
        }, report -> {
            if (report == null) {
                reportHost.getChildren().setAll(placeholder());
                return;
            }
            reportHost.getChildren().setAll(report.nodes());
            sheetName = Labels.reportName(key);
            exportHeaders = report.headers();
            exportRows = report.rows();
        }, error -> Toasts.error(error.getMessage()));
    }

    private void renderCatalogue() {
        catalogueHost.getChildren().clear();
        for (ReportCatalogueEntry entry : catalogue) {
            Card card = new Card(Labels.reportName(entry.key()), Labels.reportGroup(entry.group()));
            Label periodic = new Label(entry.periodic() ? I18n.get().t("reports.centre.periodic")
                    : I18n.get().t("reports.centre.snapshot"));
            periodic.getStyleClass().add("text-muted");
            Button open = new Button(I18n.get().t("reports.centre.open"));
            open.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
            open.setGraphic(Icons.glyph(Icons.Icon.REPORTS, 12));
            open.setOnAction(event -> show(entry.key()));
            card.body(new VBox(6, periodic, open));
            card.setUserData(entry.key());
            card.getStyleClass().add("report-card");
            card.setPrefWidth(230);
            catalogueHost.getChildren().add(card);
        }
    }

    private Label placeholder() {
        Label label = new Label(I18n.get().t("reports.centre.choose"));
        label.getStyleClass().add("text-muted");

        return label;
    }

    // ------------------------------------------------------------------ the reports

    /** One rendered report: its cards, and the table the export writes. */
    private record Rendered(List<javafx.scene.Node> nodes, List<String> headers, List<List<Object>> rows) {
    }

    private Rendered header(String key, String subtitle) {
        Label title = new Label(Labels.reportName(key));
        title.getStyleClass().add("h2");
        Label hint = new Label(subtitle);
        hint.getStyleClass().add("text-muted");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        exportButton = new Button(I18n.get().t("reports.centre.export"));
        exportButton.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        exportButton.setGraphic(Icons.glyph(Icons.Icon.EXCEL, 12));
        exportButton.setOnAction(event -> export());
        HBox row = new HBox(12, title, hint, spacer, exportButton);
        row.setAlignment(Pos.CENTER_LEFT);

        return new Rendered(List.of(row), List.of(), List.of());
    }

    private Rendered sales(SalesReport report) {
        Rendered head = header("sales", I18n.get().t("reports.centre.window",
                report.from() == null ? "—" : Formatters.shortDate(report.from()),
                report.to() == null ? "—" : Formatters.shortDate(report.to())));
        SalesReport.Totals totals = report.totals() == null
                ? new SalesReport.Totals(0, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO)
                : report.totals();
        List<javafx.scene.Node> nodes = new ArrayList<>(head.nodes());
        nodes.add(kpis(
                kpi(I18n.get().t("reports.sales.turnover"), Formatters.currency(totals.totalTtc())),
                kpi(I18n.get().t("reports.sales.ht"), Formatters.currency(totals.amountHt())),
                kpi(I18n.get().t("reports.sales.vat"), Formatters.currency(totals.vatAmount())),
                kpi(I18n.get().t("reports.sales.outstanding"), Formatters.currency(totals.outstanding()))));
        List<String> headers = List.of(I18n.get().t("common.month"), I18n.get().t("reports.sales.invoices"),
                I18n.get().t("reports.sales.ht_amount"), I18n.get().t("reports.sales.ttc_amount"));
        List<List<String>> rows = new ArrayList<>();
        for (SalesReport.Month month : report.byMonth()) {
            rows.add(List.of(month.period(), String.valueOf(month.invoiceCount()),
                    Formatters.currency(month.amountHt()), Formatters.currency(month.totalTtc())));
        }
        nodes.add(table(I18n.get().t("reports.sales.by_month"), headers, rows));
        List<String> customerHeaders = List.of(I18n.get().t("reports.sales.customer"),
                I18n.get().t("reports.sales.invoices"), I18n.get().t("reports.sales.ht_amount"),
                I18n.get().t("reports.sales.outstanding"));
        List<List<String>> customerRows = new ArrayList<>();
        for (SalesReport.Customer customer : report.byCustomer()) {
            customerRows.add(List.of(customer.name(), String.valueOf(customer.invoiceCount()),
                    Formatters.currency(customer.amountHt()), Formatters.currency(customer.outstanding())));
        }
        nodes.add(table(I18n.get().t("reports.sales.by_customer"), customerHeaders, customerRows));
        List<String> productHeaders = List.of(I18n.get().t("common.code"), I18n.get().t("common.item"),
                I18n.get().t("common.quantity"), I18n.get().t("reports.sales.revenue"));
        List<List<String>> productRows = new ArrayList<>();
        for (SalesReport.Product product : report.byProduct()) {
            productRows.add(List.of(product.sku(), product.name(), Formatters.number(product.quantity()),
                    Formatters.currency(product.revenue())));
        }
        nodes.add(table(I18n.get().t("reports.sales.by_product"), productHeaders, productRows));

        List<List<Object>> exportRows = new ArrayList<>();
        for (List<String> row : rows) {
            exportRows.add(new ArrayList<>(row));
        }

        return new Rendered(nodes, headers, exportRows);
    }

    private Rendered purchases(PurchaseReport report) {
        Rendered head = header("purchases", I18n.get().t("reports.centre.window",
                report.from() == null ? "—" : Formatters.shortDate(report.from()),
                report.to() == null ? "—" : Formatters.shortDate(report.to())));
        PurchaseReport.Totals totals = report.totals();
        List<javafx.scene.Node> nodes = new ArrayList<>(head.nodes());
        nodes.add(kpis(
                kpi(I18n.get().t("reports.purchases.orders"),
                        totals == null ? "0" : String.valueOf(totals.orderCount())),
                kpi(I18n.get().t("reports.purchases.ttc"),
                        Formatters.currency(totals == null ? null : totals.totalTtc())),
                kpi(I18n.get().t("reports.purchases.receipts"),
                        totals == null ? "0" : String.valueOf(totals.receiptCount())),
                kpi(I18n.get().t("reports.purchases.received"),
                        totals == null ? "0" : Formatters.number(totals.receivedQuantity()))));
        List<String> headers = List.of(I18n.get().t("common.status"), I18n.get().t("reports.purchases.orders"),
                I18n.get().t("reports.purchases.ttc"));
        List<List<String>> rows = new ArrayList<>();
        for (PurchaseReport.Status status : report.byStatus()) {
            rows.add(List.of(Labels.purchaseStatus(status.status()), String.valueOf(status.orderCount()),
                    Formatters.currency(status.totalTtc())));
        }
        nodes.add(table(I18n.get().t("reports.purchases.by_status"), headers, rows));
        List<String> supplierHeaders = List.of(I18n.get().t("reports.purchases.supplier"),
                I18n.get().t("reports.purchases.orders"), I18n.get().t("reports.purchases.ttc"));
        List<List<String>> supplierRows = new ArrayList<>();
        for (PurchaseReport.Supplier supplier : report.bySupplier()) {
            supplierRows.add(List.of(supplier.name(), String.valueOf(supplier.orderCount()),
                    Formatters.currency(supplier.totalTtc())));
        }
        nodes.add(table(I18n.get().t("reports.purchases.by_supplier"), supplierHeaders, supplierRows));

        List<List<Object>> exportRows = new ArrayList<>();
        for (List<String> row : rows) {
            exportRows.add(new ArrayList<>(row));
        }

        return new Rendered(nodes, headers, exportRows);
    }

    private Rendered stock(StockReport report) {
        Rendered head = header("stock", I18n.get().t("reports.stock.subtitle"));
        StockReport.Totals totals = report.totals();
        List<javafx.scene.Node> nodes = new ArrayList<>(head.nodes());
        nodes.add(kpis(
                kpi(I18n.get().t("reports.stock.value"),
                        Formatters.currency(totals == null ? null : totals.totalValue())),
                kpi(I18n.get().t("reports.stock.expiring"),
                        totals == null ? "0" : String.valueOf(totals.expiringCount())),
                kpi(I18n.get().t("reports.stock.expired"),
                        totals == null ? "0" : String.valueOf(totals.expiredCount())),
                kpi(I18n.get().t("reports.stock.quarantine"),
                        totals == null ? "0" : String.valueOf(totals.quarantineCount()))));
        List<String> headers = List.of(I18n.get().t("reports.stock.warehouse"),
                I18n.get().t("reports.stock.products"), I18n.get().t("reports.stock.materials"),
                I18n.get().t("reports.stock.total"));
        List<List<String>> rows = new ArrayList<>();
        for (StockReport.Warehouse warehouse : report.warehouses()) {
            rows.add(List.of(warehouse.name(), Formatters.currency(warehouse.productValue()),
                    Formatters.currency(warehouse.materialValue()), Formatters.currency(warehouse.totalValue())));
        }
        nodes.add(table(I18n.get().t("reports.stock.by_warehouse"), headers, rows));
        List<String> expiringHeaders = List.of(I18n.get().t("common.item"), I18n.get().t("reports.stock.batch"),
                I18n.get().t("reports.stock.expiry"), I18n.get().t("reports.stock.days_left"));
        List<List<String>> expiringRows = new ArrayList<>();
        for (StockReport.Expiring batch : report.expiring()) {
            expiringRows.add(List.of(batch.productName(), batch.batchNumber(),
                    batch.expiryDate() == null ? "—" : Formatters.shortDate(batch.expiryDate()),
                    String.valueOf(batch.daysLeft())));
        }
        nodes.add(table(I18n.get().t("reports.stock.expiring_list"), expiringHeaders, expiringRows));
        List<String> lowHeaders = List.of(I18n.get().t("common.code"), I18n.get().t("common.item"),
                I18n.get().t("reports.stock.min"), I18n.get().t("reports.stock.in_stock"));
        List<List<String>> lowRows = new ArrayList<>();
        for (StockReport.LowStock product : report.lowStock()) {
            lowRows.add(List.of(product.sku(), product.name(), Formatters.number(product.minStock()),
                    Formatters.number(product.quantity())));
        }
        nodes.add(table(I18n.get().t("reports.stock.low_list"), lowHeaders, lowRows));

        List<List<Object>> exportRows = new ArrayList<>();
        for (List<String> row : rows) {
            exportRows.add(new ArrayList<>(row));
        }

        return new Rendered(nodes, headers, exportRows);
    }

    private Rendered production(ProductionReport report) {
        Rendered head = header("production", I18n.get().t("reports.centre.window",
                report.from() == null ? "—" : Formatters.shortDate(report.from()),
                report.to() == null ? "—" : Formatters.shortDate(report.to())));
        ProductionReport.Totals totals = report.totals();
        List<javafx.scene.Node> nodes = new ArrayList<>(head.nodes());
        nodes.add(kpis(
                kpi(I18n.get().t("reports.production.orders"),
                        totals == null ? "0" : String.valueOf(totals.orderCount())),
                kpi(I18n.get().t("reports.production.produced"),
                        Formatters.number(totals == null ? null : totals.actualQuantity())),
                kpi(I18n.get().t("reports.production.yield"),
                        Formatters.percent(totals == null ? null : totals.yieldPct())),
                kpi(I18n.get().t("reports.production.cost"),
                        Formatters.currency(totals == null ? null : totals.totalCost()))));
        List<String> headers = List.of(I18n.get().t("common.code"), I18n.get().t("common.item"),
                I18n.get().t("reports.production.planned"), I18n.get().t("reports.production.produced"),
                I18n.get().t("reports.production.waste"), I18n.get().t("reports.production.cost"));
        List<List<String>> rows = new ArrayList<>();
        for (ProductionReport.Product product : report.byProduct()) {
            rows.add(List.of(product.sku(), product.name(), Formatters.number(product.plannedQuantity()),
                    Formatters.number(product.actualQuantity()), Formatters.number(product.wasteQuantity()),
                    Formatters.currency(product.totalCost())));
        }
        nodes.add(table(I18n.get().t("reports.production.by_product"), headers, rows));
        List<String> statusHeaders = List.of(I18n.get().t("common.status"),
                I18n.get().t("reports.production.orders"), I18n.get().t("reports.production.produced"));
        List<List<String>> statusRows = new ArrayList<>();
        for (ProductionReport.Status status : report.byStatus()) {
            statusRows.add(List.of(Labels.productionStatus(status.status()),
                    String.valueOf(status.orderCount()), Formatters.number(status.actualQuantity())));
        }
        nodes.add(table(I18n.get().t("reports.production.by_status"), statusHeaders, statusRows));

        List<List<Object>> exportRows = new ArrayList<>();
        for (List<String> row : rows) {
            exportRows.add(new ArrayList<>(row));
        }

        return new Rendered(nodes, headers, exportRows);
    }

    private Rendered safety(SafetyReport report) {
        Rendered head = header("safety", I18n.get().t("reports.safety.subtitle"));
        SafetyReport.Materials materials = report.materials();
        SafetyReport.Incidents incidents = report.incidents();
        List<javafx.scene.Node> nodes = new ArrayList<>(head.nodes());
        nodes.add(kpis(
                kpi(I18n.get().t("reports.safety.hazardous"),
                        materials == null ? "0" : String.valueOf(materials.hazardous())),
                kpi(I18n.get().t("reports.safety.without_sheet"),
                        materials == null ? "0" : String.valueOf(materials.withoutSheet())),
                kpi(I18n.get().t("reports.safety.obsolete"),
                        materials == null ? "0" : String.valueOf(materials.obsoleteSheets())),
                kpi(I18n.get().t("reports.safety.incidents"),
                        incidents == null ? "0" : String.valueOf(incidents.total()))));
        List<String> headers = List.of(I18n.get().t("common.code"), I18n.get().t("common.item"),
                I18n.get().t("safety.column.hazard"));
        List<List<String>> rows = new ArrayList<>();
        for (SafetyReport.MissingSheet missing : report.missingSheets()) {
            rows.add(List.of(missing.code(), missing.commercialName(),
                    Labels.hazardClass(missing.hazardClass())));
        }
        nodes.add(table(I18n.get().t("reports.safety.missing"), headers, rows));
        List<String> incidentHeaders = List.of(I18n.get().t("safety.column.severity"),
                I18n.get().t("common.status"), I18n.get().t("reports.safety.count"));
        List<List<String>> incidentRows = new ArrayList<>();
        if (incidents != null) {
            for (SafetyReport.Severity severity : incidents.bySeverity()) {
                incidentRows.add(List.of(Labels.incidentSeverity(severity.severity()),
                        Labels.incidentStatus(severity.status()), String.valueOf(severity.incidentCount())));
            }
        }
        nodes.add(table(I18n.get().t("reports.safety.by_severity"), incidentHeaders, incidentRows));

        List<List<Object>> exportRows = new ArrayList<>();
        for (List<String> row : rows) {
            exportRows.add(new ArrayList<>(row));
        }

        return new Rendered(nodes, headers, exportRows);
    }

    // ------------------------------------------------------------------ pieces

    private HBox kpis(javafx.scene.Node... cards) {
        HBox row = new HBox(12, cards);
        row.setAlignment(Pos.CENTER_LEFT);

        return row;
    }

    private VBox kpi(String label, String value) {
        Label title = new Label(label);
        title.getStyleClass().add("text-muted");
        Label amount = new Label(value == null ? "\u2014" : value);
        amount.getStyleClass().add("h3");
        VBox card = new VBox(2, title, amount);
        card.getStyleClass().addAll("card", "report-kpi");
        card.setMinWidth(170);

        return card;
    }

    private Card table(String title, List<String> headers, List<List<String>> rows) {
        Card card = new Card(title, I18n.get().t("reports.centre.rows", rows.size()));
        if (rows.isEmpty()) {
            Label empty = new Label(I18n.get().t("reports.centre.empty"));
            empty.getStyleClass().add("text-muted");
            card.body(empty);

            return card;
        }
        TableView<List<String>> tableView = new TableView<>(FXCollections.observableArrayList(rows));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.setPrefHeight(Math.min(320, 42 + rows.size() * 28));
        for (int index = 0; index < headers.size(); index++) {
            final int column = index;
            TableColumn<List<String>, String> tableColumn = new TableColumn<>(headers.get(index));
            tableColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(
                    column < data.getValue().size() ? data.getValue().get(column) : ""));
            tableColumn.setPrefWidth(160);
            tableView.getColumns().add(tableColumn);
        }
        card.body(tableView);

        return card;
    }

    private void export() {
        if (exportRows.isEmpty()) {
            Toasts.warning(I18n.get().t("dashboard.export_empty"));
            return;
        }
        Optional<Path> target = Dialogs.chooseSaveFile(AppContext.get().stage(),
                I18n.get().t("reports.centre.export"), "report.xlsx",
                I18n.get().t("dashboard.export_filter"), "xlsx");
        if (target.isEmpty()) {
            return;
        }
        List<String> headers = exportHeaders;
        List<List<Object>> rows = exportRows;
        String sheet = sheetName;
        Path file = target.get();
        FxTasks.runVoid(() -> ExcelExporter.exportRows(file, sheet, headers, rows),
                () -> Toasts.success(I18n.get().t("dashboard.export_done", file.getFileName().toString())),
                error -> Toasts.error(I18n.get().t("dashboard.export_failed", String.valueOf(error.getMessage()))));
    }

    /** The badge of a report family, so the catalogue and the screen agree. */
    static Label groupBadge(String group) {
        return StatusBadge.of(Labels.reportGroup(group), "badge-info");
    }
}
