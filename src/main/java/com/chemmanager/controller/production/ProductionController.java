package com.chemmanager.controller.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductionQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.model.production.ApprovedVersion;
import com.chemmanager.model.production.ProductionGenealogy;
import com.chemmanager.model.production.ProductionOrder;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Manufacturing orders: what the shop floor has to produce, what it consumed, what quality released
 * and where every finished batch came from.
 *
 * <p>The screen records; it never proposes a formula. An order starts from an approved version a
 * manager wrote, and the buttons follow the order's state so a button can never ask the server for
 * something it will refuse.</p>
 */
public final class ProductionController {

    private static final List<String> STATUSES = List.of("", "DRAFT", "WAITING_MATERIALS", "IN_PROGRESS",
            "QUALITY_CONTROL", "COMPLETED", "REJECTED", "CANCELLED");

    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button newButton;
    @FXML private Button startButton;
    @FXML private Button consumeButton;
    @FXML private Button completeButton;
    @FXML private Button qualityButton;
    @FXML private Button genealogyButton;
    @FXML private Button refreshButton;

    private DataTable<ProductionOrder> table;
    private ProductionQuery query = ProductionQuery.initial(25);
    private ProductionOrder selected;

    @FXML
    private void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList(STATUSES));
        statusFilter.setButtonCell(statusCell());
        statusFilter.setCellFactory(list -> statusCell());
        statusFilter.getSelectionModel().select("");
        statusFilter.setOnAction(event -> {
            String value = statusFilter.getValue();
            query = query.withStatus(value == null ? "" : value);
            table.resetToFirstPage();
        });

        newButton.setOnAction(event -> createOrder());
        startButton.setOnAction(event -> startOrder());
        consumeButton.setOnAction(event -> consumeMaterial());
        completeButton.setOnAction(event -> completeOrder());
        qualityButton.setOnAction(event -> recordQuality());
        genealogyButton.setOnAction(event -> showGenealogy());
        refreshButton.setOnAction(event -> {
            table.resetToFirstPage();
            if (selected != null) {
                select(selected);
            }
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("production.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("production.column.number"), 110,
                                ProductionOrder::orderNumber),
                        DataTable.Column.text(I18n.get().t("production.column.product"), 220,
                                ProductionOrder::productName),
                        DataTable.Column.text(I18n.get().t("production.column.version"), 90,
                                order -> order.formulationCode() + " v" + order.formulationVersion()),
                        DataTable.Column.date(I18n.get().t("production.column.planned_date"), 105,
                                ProductionOrder::plannedDate),
                        DataTable.Column.quantity(I18n.get().t("production.column.planned_quantity"), 105,
                                order -> Formatters.quantity(order.plannedQuantity(), order.unit())),
                        DataTable.Column.quantity(I18n.get().t("production.column.consumed"), 100,
                                order -> Formatters.quantity(order.consumedQuantity(), order.unit())),
                        DataTable.Column.amount(I18n.get().t("production.column.cost"), 120,
                                ProductionOrder::totalCost),
                        DataTable.Column.node(I18n.get().t("common.status"), 130,
                                order -> StatusBadge.production(order.status())))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("production.summary", page.total()));
                    if (selected == null && !page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        VBox.setVgrow(table, Priority.ALWAYS);
        tableHost.getChildren().setAll(table);

        showHint();
        updateActions(null);
        table.resetToFirstPage();
    }

    private PagedResult<ProductionOrder> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.production().search(query);
    }

    private ListCell<String> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null || item.isEmpty()
                        ? I18n.get().t("production.filter.all") : Labels.productionStatus(item));
            }
        };
    }

    // ------------------------------------------------------------------ detail

    private void showHint() {
        Label hint = new Label(I18n.get().t("production.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void select(ProductionOrder order) {
        selected = order;
        updateActions(order);
        FxTasks.run(() -> ServiceFactory.production().load(order.id()), this::renderDetail,
                error -> Toasts.error(message(error)));
    }

    private void renderDetail(ProductionOrder order) {
        selected = order;
        updateActions(order);

        Card card = new Card(order.orderNumber() + " · " + order.productName(),
                order.formulationCode() + " v" + order.formulationVersion() + " · " + order.warehouseName());
        card.action(StatusBadge.production(order.status()));
        card.body(new VBox(6,
                fact("production.column.planned_date", order.plannedDate() == null ? "—"
                        : Formatters.shortDate(order.plannedDate())),
                fact("production.column.planned_quantity", Formatters.quantity(order.plannedQuantity(), order.unit())),
                fact("production.column.actual_quantity", order.actualQuantity().signum() == 0 ? "—"
                        : Formatters.quantity(order.actualQuantity(), order.unit())),
                fact("production.column.waste", order.wasteQuantity().signum() == 0 ? "—"
                        : Formatters.quantity(order.wasteQuantity(), order.unit())),
                fact("production.cost.material", Formatters.currency(order.materialCost())),
                fact("production.cost.packaging", Formatters.currency(order.packagingCost())),
                fact("production.cost.other", Formatters.currency(order.otherCost())),
                fact("production.cost.total", Formatters.currency(order.totalCost())),
                fact("production.cost.unit", order.costPerUnit().signum() == 0 ? "—"
                        : Formatters.currency(order.costPerUnit())),
                fact("production.cost.standard", Formatters.currency(order.standardMaterialCost())),
                fact("production.cost.variance", Formatters.currency(order.variance())
                        + " (" + Formatters.percent(order.variancePct()) + ")")));

        VBox box = new VBox(12, card);

        box.getChildren().add(FormFields.sectionTitle("production.materials"));
        if (order.materials().isEmpty()) {
            box.getChildren().add(FormFields.description("production.materials.empty"));
        } else {
            box.getChildren().add(materialsTable(order));
        }

        box.getChildren().add(FormFields.sectionTitle("production.outputs"));
        if (order.outputs().isEmpty()) {
            box.getChildren().add(FormFields.description("production.outputs.empty"));
        } else {
            for (ProductionOrder.Output output : order.outputs()) {
                Label line = new Label(output.batchNumber() + " · "
                        + Formatters.quantity(output.quantity(), output.unit()) + " · "
                        + I18n.get().t("production.output.sellable",
                        output.sellable() ? I18n.get().t("common.yes") : I18n.get().t("common.no")));
                line.getStyleClass().add("text-muted");
                line.setWrapText(true);
                box.getChildren().addAll(line, StatusBadge.of(Labels.quality(output.qualityStatus()),
                        Labels.qualityStyle(output.qualityStatus())));
            }
        }

        box.getChildren().add(FormFields.sectionTitle("production.quality"));
        if (order.qualityChecks().isEmpty()) {
            box.getChildren().add(FormFields.description("production.quality.empty"));
        } else {
            for (ProductionOrder.QualityCheck check : order.qualityChecks()) {
                Label line = new Label(check.checkType() + " · " + check.result() + " · "
                        + (check.checkedByName().isBlank() ? "—" : check.checkedByName())
                        + (check.remarks().isBlank() ? "" : " · " + check.remarks()));
                line.getStyleClass().add("text-muted");
                line.setWrapText(true);
                box.getChildren().add(line);
            }
        }

        detailHost.getChildren().setAll(box);
    }

    private TableView<ProductionOrder.MaterialLine> materialsTable(ProductionOrder order) {
        TableView<ProductionOrder.MaterialLine> items = new TableView<>();
        items.setPrefHeight(200);
        items.setItems(FXCollections.observableArrayList(order.materials()));
        items.setPlaceholder(new Label(I18n.get().t("production.materials.empty")));

        TableColumn<ProductionOrder.MaterialLine, String> material = new TableColumn<>(
                I18n.get().t("production.column.material"));
        material.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().materialCode() + " · " + cell.getValue().batchNumber()));
        material.setPrefWidth(230);

        TableColumn<ProductionOrder.MaterialLine, String> planned = new TableColumn<>(
                I18n.get().t("production.column.planned_quantity"));
        planned.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().plannedQuantity(), cell.getValue().unit())));
        planned.setPrefWidth(110);

        TableColumn<ProductionOrder.MaterialLine, String> consumed = new TableColumn<>(
                I18n.get().t("production.column.consumed"));
        consumed.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().consumedQuantity(), cell.getValue().unit())));
        consumed.setPrefWidth(110);

        TableColumn<ProductionOrder.MaterialLine, String> variance = new TableColumn<>(
                I18n.get().t("production.column.variance"));
        variance.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.quantity(cell.getValue().varianceQuantity(), cell.getValue().unit())));
        variance.setPrefWidth(100);

        items.getColumns().addAll(material, planned, consumed, variance);

        return items;
    }

    private void updateActions(ProductionOrder order) {
        String status = order == null ? "" : order.status();
        boolean hasOrder = order != null;
        startButton.setDisable(!hasOrder || !(status.equals("DRAFT") || status.equals("WAITING_MATERIALS")));
        consumeButton.setDisable(!hasOrder || !status.equals("IN_PROGRESS"));
        completeButton.setDisable(!hasOrder || !status.equals("IN_PROGRESS"));
        qualityButton.setDisable(!hasOrder || !status.equals("QUALITY_CONTROL"));
        genealogyButton.setDisable(!hasOrder);
        newButton.setDisable(false);
    }

    // ------------------------------------------------------------------ actions

    private void createOrder() {
        FxTasks.run(() -> new Draft(
                        ServiceFactory.production().versions(),
                        ServiceFactory.inventory().warehouses()),
                this::openCreateDialog,
                error -> Toasts.error(message(error)));
    }

    private void openCreateDialog(Draft draft) {
        List<ApprovedVersion> versions = draft.versions().stream()
                .filter(version -> version.productId() > 0).toList();
        if (versions.isEmpty()) {
            Toasts.warning(I18n.get().t("production.no_version"));
            return;
        }
        List<Warehouse> warehouses = draft.warehouses().stream().filter(warehouse -> !warehouse.quarantine()).toList();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(table.getScene() == null ? null : table.getScene().getWindow());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("production.new.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<ApprovedVersion> versionBox = new ComboBox<>(FXCollections.observableArrayList(versions));
        versionBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ApprovedVersion item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.formulationCode() + " v" + item.version() + " · " + item.productName());
            }
        });
        versionBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ApprovedVersion item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.formulationCode() + " v" + item.version() + " · " + item.productName());
            }
        });
        versionBox.getSelectionModel().selectFirst();

        ComboBox<Warehouse> warehouseBox = new ComboBox<>(FXCollections.observableArrayList(warehouses));
        warehouseBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Warehouse item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.code() + " · " + item.name());
            }
        });
        warehouseBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Warehouse item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.code() + " · " + item.name());
            }
        });
        warehouseBox.getSelectionModel().selectFirst();

        TextField quantity = new TextField();
        DatePicker date = new DatePicker(LocalDate.now());
        TextArea notes = new TextArea();
        notes.setPrefRowCount(2);

        VBox content = new VBox(12,
                FormFields.fieldKey("production.new.version", versionBox, "production.new.version_hint"),
                FormFields.row(FormFields.field("production.new.quantity", quantity),
                        FormFields.field("production.new.date", date)),
                FormFields.field("production.new.warehouse", warehouseBox),
                FormFields.field("production.new.notes", notes));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        ApprovedVersion version = versionBox.getValue();
        Warehouse warehouse = warehouseBox.getValue();
        BigDecimal planned = Formatters.parseDecimal(quantity.getText());
        if (version == null || warehouse == null || planned.signum() <= 0) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }

        ProductionOrder.NewOrder request = new ProductionOrder.NewOrder(version.versionId(), warehouse.id(),
                planned, date.getValue(), notes.getText());
        FxTasks.run(() -> ServiceFactory.production().create(request), created -> {
            Toasts.success(I18n.get().t("production.toast.created", created.orderNumber()));
            table.resetToFirstPage();
            select(created);
        }, error -> Toasts.error(message(error)));
    }

    private void startOrder() {
        if (selected == null) {
            return;
        }
        ProductionOrder order = selected;
        if (!Dialogs.confirm(window(), I18n.get().t("production.action.start"),
                I18n.get().t("production.confirm.start", order.orderNumber()))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.production().start(order.id()), started -> {
            Toasts.success(I18n.get().t("production.toast.started", started.orderNumber()));
            renderDetail(started);
        }, error -> Toasts.error(message(error)));
    }

    private void consumeMaterial() {
        if (selected == null || selected.materials().isEmpty()) {
            Toasts.warning(I18n.get().t("production.materials.empty"));
            return;
        }
        ProductionOrder order = selected;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("production.consume.title", order.orderNumber()));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        Map<Long, TextField> fields = new LinkedHashMap<>();
        VBox rows = new VBox(10);
        for (ProductionOrder.MaterialLine line : order.materials()) {
            BigDecimal remaining = line.plannedQuantity().subtract(line.consumedQuantity());
            TextField input = new TextField(remaining.signum() > 0 ? remaining.toPlainString() : "0");
            fields.put(line.materialBatchId(), input);
            rows.getChildren().add(FormFields.field(
                    line.materialCode() + " · " + line.batchNumber() + " (" + line.unit() + ")",
                    input, I18n.get().t("production.consume.hint",
                            Formatters.quantity(line.plannedQuantity(), line.unit()),
                            Formatters.quantity(line.consumedQuantity(), line.unit()))));
        }
        dialog.getDialogPane().setContent(new VBox(12, FormFields.description("production.consume.intro"), rows));
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        List<ProductionOrder.Consumption> items = new ArrayList<>();
        for (Map.Entry<Long, TextField> entry : fields.entrySet()) {
            BigDecimal quantity = Formatters.parseDecimal(entry.getValue().getText());
            if (quantity.signum() > 0) {
                items.add(new ProductionOrder.Consumption(entry.getKey(), quantity));
            }
        }
        if (items.isEmpty()) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }

        FxTasks.run(() -> ServiceFactory.production().consume(order.id(), items), consumed -> {
            Toasts.success(I18n.get().t("production.toast.consumed", Formatters.currency(consumed.materialCost())));
            renderDetail(consumed);
        }, error -> Toasts.error(message(error)));
    }

    private void completeOrder() {
        if (selected == null) {
            return;
        }
        ProductionOrder order = selected;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("production.complete.title", order.orderNumber()));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField actual = new TextField(order.plannedQuantity().toPlainString());
        TextField waste = new TextField("0");
        TextField packaging = new TextField("0");
        TextField other = new TextField("0");
        DatePicker expiry = new DatePicker(LocalDate.now().plusMonths(18));

        VBox content = new VBox(12,
                FormFields.description("production.complete.intro"),
                FormFields.row(FormFields.field("production.column.actual_quantity", actual),
                        FormFields.field("production.column.waste", waste)),
                FormFields.row(FormFields.field("production.cost.packaging", packaging),
                        FormFields.field("production.cost.other", other)),
                FormFields.field("production.complete.expiry", expiry));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        BigDecimal actualQuantity = Formatters.parseDecimal(actual.getText());
        if (actualQuantity.signum() <= 0) {
            Toasts.warning(I18n.get().t("validation.positive"));
            return;
        }
        ProductionOrder.Completion completion = new ProductionOrder.Completion(actualQuantity,
                Formatters.parseDecimal(waste.getText()), Formatters.parseDecimal(packaging.getText()),
                Formatters.parseDecimal(other.getText()), expiry.getValue());

        FxTasks.run(() -> ServiceFactory.production().complete(order.id(), completion), completed -> {
            Toasts.success(I18n.get().t("production.toast.completed", Formatters.currency(completed.totalCost())));
            renderDetail(completed);
        }, error -> Toasts.error(message(error)));
    }

    private void recordQuality() {
        if (selected == null) {
            return;
        }
        ProductionOrder order = selected;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("production.quality.title", order.orderNumber()));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(
                "aspect", "pH", "viscosity", "density", "activeMatter"));
        type.getSelectionModel().selectFirst();
        ComboBox<String> result = new ComboBox<>(FXCollections.observableArrayList("PASS", "FAIL"));
        result.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : I18n.get().t("production.quality." + item.toLowerCase(java.util.Locale.ROOT)));
            }
        });
        result.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : I18n.get().t("production.quality." + item.toLowerCase(java.util.Locale.ROOT)));
            }
        });
        result.getSelectionModel().selectFirst();
        TextField remarks = new TextField();

        VBox content = new VBox(12,
                FormFields.description("production.quality.intro"),
                FormFields.row(FormFields.field("production.quality.check_type", type),
                        FormFields.field("production.quality.result", result)),
                FormFields.field("production.quality.remarks", remarks));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        String checkType = type.getValue();
        String outcome = result.getValue();
        if (checkType == null || outcome == null) {
            return;
        }
        ProductionOrder.QualityDecision decision = new ProductionOrder.QualityDecision(checkType, outcome,
                remarks.getText());

        FxTasks.run(() -> ServiceFactory.production().quality(order.id(), decision), checked -> {
            Toasts.success(I18n.get().t("PASS".equals(decision.result())
                    ? "production.toast.released" : "production.toast.rejected", checked.orderNumber()));
            renderDetail(checked);
        }, error -> Toasts.error(message(error)));
    }

    private void showGenealogy() {
        if (selected == null) {
            return;
        }
        ProductionOrder order = selected;
        FxTasks.run(() -> ServiceFactory.production().genealogy(order.id()), genealogy -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(window());
            Dialogs.applyTheme(dialog.getDialogPane());
            dialog.setTitle(I18n.get().t("production.genealogy.title", genealogy.orderNumber()));
            dialog.getDialogPane().getButtonTypes().add(new ButtonType(I18n.get().t("common.close"),
                    ButtonBar.ButtonData.OK_DONE));
            dialog.getDialogPane().setContent(genealogyBody(genealogy));
            dialog.getDialogPane().setPrefWidth(760);
            dialog.showAndWait();
        }, error -> Toasts.error(message(error)));
    }

    private VBox genealogyBody(ProductionGenealogy genealogy) {
        VBox body = new VBox(8);
        Label chain = new Label(I18n.get().t(genealogy.complete()
                ? "production.genealogy.complete_chain" : "production.genealogy.incomplete_chain"));
        chain.getStyleClass().add("text-muted");
        chain.setWrapText(true);
        body.getChildren().addAll(chain, FormFields.sectionTitle("production.genealogy.inputs"));

        if (genealogy.inputs().isEmpty()) {
            body.getChildren().add(FormFields.description("production.materials.empty"));
        }
        for (ProductionGenealogy.Input input : genealogy.inputs()) {
            Label line = new Label(input.materialCode() + " · " + input.batchNumber() + " · "
                    + Formatters.quantity(input.consumedQuantity(), input.unit()) + " · "
                    + I18n.get().t("production.genealogy.trace",
                    input.supplierName().isBlank() ? "—" : input.supplierName(),
                    input.receiptNumber().isBlank() ? "—" : input.receiptNumber(),
                    input.purchaseOrderNumber().isBlank() ? "—" : input.purchaseOrderNumber()));
            line.getStyleClass().add("text-muted");
            line.setWrapText(true);
            body.getChildren().add(line);
        }

        body.getChildren().add(FormFields.sectionTitle("production.genealogy.outputs"));
        for (ProductionOrder.Output output : genealogy.outputs()) {
            Label line = new Label(output.batchNumber() + " · "
                    + Formatters.quantity(output.quantity(), output.unit()) + " · "
                    + Labels.quality(output.qualityStatus()));
            line.getStyleClass().add("text-muted");
            body.getChildren().add(line);
        }

        return body;
    }

    // ------------------------------------------------------------------ helpers

    private javafx.stage.Window window() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }

    private Label fact(String labelKey, String value) {
        Label node = new Label(I18n.get().t(labelKey) + " : "
                + (value == null || value.isBlank() ? "—" : value));
        node.getStyleClass().add("text-muted");
        node.setWrapText(true);

        return node;
    }

    private static String message(Throwable error) {
        String message = error == null ? null : error.getMessage();

        return message == null || message.isBlank() ? I18n.get().t("error.generic") : message;
    }

    /** What the create dialog needs, loaded off the JavaFX thread. */
    private record Draft(List<ApprovedVersion> versions, List<Warehouse> warehouses) {
    }
}
