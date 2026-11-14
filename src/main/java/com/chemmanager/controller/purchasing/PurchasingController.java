package com.chemmanager.controller.purchasing;

import java.util.List;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.PurchaseQuery;
import com.chemmanager.dto.SupplierQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.RawMaterial;
import com.chemmanager.model.Supplier;
import com.chemmanager.model.inventory.Warehouse;
import com.chemmanager.model.purchasing.PurchaseOrder;
import com.chemmanager.model.purchasing.PurchaseOrderDraft;
import com.chemmanager.model.purchasing.ReceptionDraft;
import com.chemmanager.security.Permission;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Purchasing: from the need to the stock.
 *
 * <p>An order is created as a DRAFT, sent to the supplier, then received — fully or line by line.
 * Each reception creates the batch, its expiry and its ledger entry on the server, and the order
 * moves to PARTIAL then RECEIVED on its own. Once something has arrived the order can no longer be
 * cancelled, because the stock is real.</p>
 */
public final class PurchasingController {

    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button refreshButton;
    @FXML private Button newButton;

    private DataTable<PurchaseOrder> table;
    private PurchaseQuery query = PurchaseQuery.initial(25);
    private PurchaseOrder selected;

    @FXML
    private void initialize() {
        newButton.setGraphic(Icons.glyph(Icons.Icon.PLUS, 12));
        newButton.setOnAction(event -> createOrder());
        newButton.setDisable(!canCreate());
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> table.resetToFirstPage());

        statusFilter.setItems(FXCollections.observableArrayList("", "DRAFT", "SENT", "PARTIAL", "RECEIVED",
                "CANCELLED"));
        statusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all")
                        : Labels.purchaseStatus(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(event -> {
            query = query.withStatus(statusFilter.getValue());
            table.resetToFirstPage();
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("purchasing.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("purchasing.order_number"), 110,
                                PurchaseOrder::orderNumber),
                        DataTable.Column.text(I18n.get().t("supplier.name"), 210,
                                PurchaseOrder::supplierName),
                        DataTable.Column.node(I18n.get().t("common.status"), 115,
                                order -> StatusBadge.of(Labels.purchaseStatus(order.status()),
                                        Labels.purchaseStatusStyle(order.status()))),
                        DataTable.Column.date(I18n.get().t("purchasing.order_date"), 110,
                                PurchaseOrder::orderDate),
                        DataTable.Column.date(I18n.get().t("purchasing.expected_date"), 110,
                                PurchaseOrder::expectedDate),
                        DataTable.Column.text(I18n.get().t("purchasing.column.progress"), 130,
                                order -> Formatters.number(order.receivedQuantity()) + " / "
                                        + Formatters.number(order.itemCount())),
                        DataTable.Column.amount(I18n.get().t("purchasing.amount_ht"), 120,
                                PurchaseOrder::amountHt),
                        DataTable.Column.amount(I18n.get().t("purchasing.total_ttc"), 130,
                                PurchaseOrder::totalTtc),
                        DataTable.Column.text(I18n.get().t("purchasing.column.receipts"), 95,
                                order -> Long.toString(order.receiptCount())))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("purchasing.summary", page.total()));
                    if (selected == null && !page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        VBox.setVgrow(table, Priority.ALWAYS);
        tableHost.getChildren().setAll(table);

        showHint();
        table.resetToFirstPage();
    }

    // ------------------------------------------------------------------ data

    private PagedResult<PurchaseOrder> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.purchasing().search(query);
    }

    private void select(PurchaseOrder order) {
        selected = order;
        FxTasks.run(() -> ServiceFactory.purchasing().load(order.id()), this::renderDetail,
                error -> Toasts.error(message(error)));
    }

    private void apply(PurchaseOrder order) {
        selected = order;
        table.reload();
        renderDetail(order);
    }

    // ------------------------------------------------------------------ detail

    private void showHint() {
        Label hint = new Label(I18n.get().t("purchasing.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderDetail(PurchaseOrder order) {
        Card header = new Card(order.orderNumber(),
                order.supplierName() + " · " + Labels.purchaseStatus(order.status()));
        VBox facts = new VBox(5,
                fact("purchasing.order_date", Formatters.shortDate(order.orderDate())),
                fact("purchasing.expected_date", order.expectedDate() == null ? "—"
                        : Formatters.shortDate(order.expectedDate())),
                fact("purchasing.column.received", Formatters.number(order.receivedQuantity())),
                fact("purchasing.amount_ht", Formatters.currency(order.amountHt())),
                fact("purchasing.vat", Formatters.currency(order.vatAmount())),
                fact("purchasing.total_ttc", Formatters.currency(order.totalTtc(), order.currency())),
                fact("purchasing.notes", order.notes()));

        Button send = actionButton("purchasing.action.send", "btn-primary",
                order.isDraft() && canCreate(), () -> sendOrder(order));
        Button receive = actionButton("purchasing.action.receive", "btn-secondary",
                order.canBeReceived() && canApprove(), () -> receiveOrder(order));
        Button cancel = actionButton("purchasing.action.cancel", "btn-ghost",
                order.canBeCancelled() && canCreate(), () -> cancelOrder(order));
        HBox actions = new HBox(8, send, receive, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);
        facts.getChildren().add(actions);
        header.body(facts);

        Label linesTitle = new Label(I18n.get().t("purchasing.detail.lines"));
        linesTitle.getStyleClass().add("h3");
        VBox box = new VBox(10, header, linesTitle, itemsTable(order));

        Label receiptsTitle = new Label(I18n.get().t("purchasing.detail.receipts"));
        receiptsTitle.getStyleClass().add("h3");
        box.getChildren().add(receiptsTitle);
        if (order.receipts().isEmpty()) {
            Label empty = new Label(I18n.get().t("purchasing.detail.no_receipts"));
            empty.getStyleClass().add("text-muted");
            box.getChildren().add(empty);
        } else {
            for (PurchaseOrder.Receipt receipt : order.receipts()) {
                Label line = new Label(receipt.receiptNumber() + " · " + receipt.warehouseName() + " · "
                        + (receipt.receivedAt() == null ? "—" : Formatters.dateTime(receipt.receivedAt()))
                        + " · " + I18n.get().t("purchasing.receive.items", receipt.itemCount()));
                line.getStyleClass().add("text-muted");
                box.getChildren().add(line);
            }
        }
        detailHost.getChildren().setAll(box);
    }

    private TableView<PurchaseOrder.Line> itemsTable(PurchaseOrder order) {
        TableView<PurchaseOrder.Line> items = new TableView<>();
        items.setPrefHeight(200);
        items.setItems(FXCollections.observableArrayList(order.items()));
        items.setPlaceholder(new Label(I18n.get().t("purchasing.detail.no_lines")));

        TableColumn<PurchaseOrder.Line, String> code = column(I18n.get().t("material.code"), 100,
                line -> line.materialCode());
        TableColumn<PurchaseOrder.Line, String> name = column(I18n.get().t("material.name"), 220,
                line -> line.materialName());
        TableColumn<PurchaseOrder.Line, String> ordered = column(I18n.get().t("common.quantity"), 105,
                line -> Formatters.quantity(line.quantity(), line.unit()));
        TableColumn<PurchaseOrder.Line, String> received = column(I18n.get().t("purchasing.column.received"), 105,
                line -> Formatters.quantity(line.receivedQty(), line.unit()));
        TableColumn<PurchaseOrder.Line, String> remaining = column(I18n.get().t("purchasing.column.remaining"), 105,
                line -> Formatters.quantity(line.remainingQty(), line.unit()));
        TableColumn<PurchaseOrder.Line, String> price = column(I18n.get().t("purchasing.unit_price"), 120,
                line -> Formatters.currency(line.unitPrice()));
        TableColumn<PurchaseOrder.Line, String> total = column(I18n.get().t("purchasing.line_total"), 130,
                line -> Formatters.currency(line.lineTotal()));
        items.getColumns().addAll(code, name, ordered, received, remaining, price, total);

        return items;
    }

    private static TableColumn<PurchaseOrder.Line, String> column(String title, double width,
                                                                  java.util.function.Function<PurchaseOrder.Line,
                                                                          String> value) {
        TableColumn<PurchaseOrder.Line, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);

        return column;
    }

    private Label fact(String labelKey, String value) {
        Label node = new Label(I18n.get().t(labelKey) + " : "
                + (value == null || value.isBlank() ? "—" : value));
        node.getStyleClass().add("text-muted");

        return node;
    }

    private Button actionButton(String labelKey, String styleClass, boolean enabled, Runnable action) {
        Button button = new Button(I18n.get().t(labelKey));
        button.getStyleClass().addAll("btn", styleClass, "btn-sm");
        button.setDisable(!enabled);
        button.setOnAction(event -> action.run());

        return button;
    }

    // ------------------------------------------------------------------ actions

    private void createOrder() {
        FxTasks.run(this::draftChoices, choices -> {
            Optional<PurchaseOrderDraft> draft = PurchaseOrderDialog.open(window(), choices.suppliers(),
                    choices.materials());
            draft.ifPresent(value -> FxTasks.runVoid(() -> ServiceFactory.purchasing().create(value),
                    created -> {
                        Toasts.success(I18n.get().t("purchasing.created", created.orderNumber()));
                        table.resetToFirstPage();
                        apply(created);
                    },
                    error -> Toasts.error(message(error))));
        }, error -> Toasts.error(message(error)));
    }

    private DraftChoices draftChoices() {
        PagedResult<Supplier> suppliers = ServiceFactory.suppliers().search(SupplierQuery.initial(200));
        List<RawMaterial> materials = ServiceFactory.rawMaterials()
                .search(com.chemmanager.dto.MaterialQuery.initial(400)).items();

        return new DraftChoices(suppliers.items(), materials);
    }

    private void sendOrder(PurchaseOrder order) {
        if (!Dialogs.confirm(window(), I18n.get().t("purchasing.action.send"),
                I18n.get().t("purchasing.send_confirm", order.orderNumber(), order.supplierName()))) {
            return;
        }
        FxTasks.runVoid(() -> ServiceFactory.purchasing().send(order.id()),
                updated -> {
                    Toasts.success(I18n.get().t("purchasing.sent", updated.orderNumber()));
                    apply(updated);
                },
                error -> Toasts.error(message(error)));
    }

    private void cancelOrder(PurchaseOrder order) {
        if (!Dialogs.confirm(window(), I18n.get().t("purchasing.action.cancel"),
                I18n.get().t("purchasing.cancel_confirm", order.orderNumber()))) {
            return;
        }
        FxTasks.runVoid(() -> ServiceFactory.purchasing().cancel(order.id()),
                updated -> {
                    Toasts.success(I18n.get().t("purchasing.cancelled", updated.orderNumber()));
                    apply(updated);
                },
                error -> Toasts.error(message(error)));
    }

    private void receiveOrder(PurchaseOrder order) {
        FxTasks.run(() -> ServiceFactory.inventory().warehouses(), warehouses -> {
            Optional<ReceptionDraft> draft = GoodsReceiptDialog.open(window(), order, warehouses);
            draft.ifPresent(value -> FxTasks.runVoid(() -> ServiceFactory.purchasing().receive(order.id(), value),
                    receipt -> {
                        Toasts.success(I18n.get().t("purchasing.received", receipt.receiptNumber(),
                                Labels.purchaseStatus(receipt.orderStatus())));
                        table.reload();
                        select(order);
                    },
                    error -> Toasts.error(message(error))));
        }, error -> Toasts.error(message(error)));
    }

    private javafx.stage.Window window() {
        return tableHost.getScene() == null ? null : tableHost.getScene().getWindow();
    }

    private static boolean canCreate() {
        return SessionContext.get().can(Permission.PURCHASING_CREATE);
    }

    private static boolean canApprove() {
        return SessionContext.get().can(Permission.PURCHASING_APPROVE);
    }

    private static String message(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) {
            return I18n.get().t("error.generic");
        }
        if (message.contains("PO_INVALID_STATE")) {
            return I18n.get().t("purchasing.error.state");
        }
        if (message.contains("PO_ALREADY_RECEIVED")) {
            return I18n.get().t("purchasing.error.already_received");
        }
        if (message.contains("over_receipt") || message.contains("remain to be received")) {
            return I18n.get().t("purchasing.error.over_receipt");
        }
        if (message.contains("QUARANTINE")) {
            return I18n.get().t("inventory.error.quarantine");
        }

        return message;
    }

    private record DraftChoices(List<Supplier> suppliers, List<RawMaterial> materials) {
    }
}
