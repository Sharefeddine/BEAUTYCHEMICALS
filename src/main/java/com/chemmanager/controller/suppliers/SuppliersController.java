package com.chemmanager.controller.suppliers;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Icons;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.SupplierQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.Supplier;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Suppliers: who we buy from, what they supply and what we still owe them.
 *
 * <p>The list is the entry point; selecting a supplier loads what it sells us and its last orders,
 * so a buyer can decide where a need goes without leaving the screen.</p>
 */
public final class SuppliersController {

    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private Label summaryLabel;
    @FXML private CheckBox activeOnly;
    @FXML private Button refreshButton;

    private DataTable<Supplier> table;
    private SupplierQuery query = SupplierQuery.initial(25);
    private Supplier selected;

    @FXML
    private void initialize() {
        activeOnly.setText(I18n.get().t("suppliers.active_only"));
        activeOnly.setOnAction(event -> {
            query = query.withActiveOnly(activeOnly.isSelected());
            table.resetToFirstPage();
        });
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> table.resetToFirstPage());

        table = new DataTable<>(this::loadPage, I18n.get().t("suppliers.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("common.code"), 100, Supplier::code),
                        DataTable.Column.text(I18n.get().t("supplier.name"), 230, Supplier::name),
                        DataTable.Column.text(I18n.get().t("suppliers.contact"), 150, Supplier::contactName),
                        DataTable.Column.text(I18n.get().t("suppliers.phone"), 130, Supplier::phone),
                        DataTable.Column.text(I18n.get().t("suppliers.wilaya"), 120, Supplier::wilaya),
                        DataTable.Column.text(I18n.get().t("suppliers.terms"), 130, Supplier::paymentTerms),
                        DataTable.Column.amount(I18n.get().t("suppliers.balance"), 130, Supplier::balance),
                        DataTable.Column.quantity(I18n.get().t("suppliers.column.materials"), 95,
                                supplier -> Long.toString(supplier.materialCount())),
                        DataTable.Column.node(I18n.get().t("common.status"), 100,
                                supplier -> StatusBadge.active(supplier.active())))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("suppliers.summary", page.total()));
                    if (selected == null && !page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        VBox.setVgrow(table, Priority.ALWAYS);
        tableHost.getChildren().setAll(table);

        showHint();
        table.resetToFirstPage();
    }

    private PagedResult<Supplier> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.suppliers().search(query);
    }

    private void select(Supplier supplier) {
        selected = supplier;
        FxTasks.run(() -> ServiceFactory.suppliers().load(supplier.id()), this::renderDetail,
                error -> Toasts.error(message(error)));
    }

    private void showHint() {
        Label hint = new Label(I18n.get().t("suppliers.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderDetail(Supplier supplier) {
        Card card = new Card(supplier.code() + " · " + supplier.name(), supplier.paymentTerms());
        VBox body = new VBox(6,
                fact("suppliers.contact", supplier.contactName()),
                fact("suppliers.phone", supplier.phone()),
                fact("suppliers.email", supplier.email()),
                fact("suppliers.wilaya", supplier.wilaya()),
                fact("suppliers.address", supplier.address()),
                fact("suppliers.balance", Formatters.currency(supplier.balance())),
                fact("suppliers.last_order", supplier.lastOrderDate() == null ? "—"
                        : Formatters.shortDate(supplier.lastOrderDate())),
                fact("suppliers.orders", Long.toString(supplier.orderCount())));
        card.body(body);

        Label materialsTitle = new Label(I18n.get().t("suppliers.materials"));
        materialsTitle.getStyleClass().add("h3");
        VBox box = new VBox(10, card, materialsTitle);
        if (supplier.materials().isEmpty()) {
            Label empty = new Label(I18n.get().t("suppliers.materials_empty"));
            empty.getStyleClass().add("text-muted");
            box.getChildren().add(empty);
        } else {
            box.getChildren().add(materialsTable(supplier));
        }
        if (!supplier.recentOrders().isEmpty()) {
            Label ordersTitle = new Label(I18n.get().t("suppliers.recent_orders"));
            ordersTitle.getStyleClass().add("h3");
            box.getChildren().add(ordersTitle);
            for (Supplier.Order order : supplier.recentOrders()) {
                Label line = new Label(order.orderNumber() + " · " + Labels.purchaseStatus(order.status())
                        + " · " + Formatters.currency(order.totalTtc()));
                line.getStyleClass().add("text-muted");
                box.getChildren().add(line);
            }
        }
        detailHost.getChildren().setAll(box);
    }

    private TableView<Supplier.Material> materialsTable(Supplier supplier) {
        TableView<Supplier.Material> items = new TableView<>();
        items.setPrefHeight(220);
        items.setItems(FXCollections.observableArrayList(supplier.materials()));
        items.setPlaceholder(new Label(I18n.get().t("suppliers.materials_empty")));

        TableColumn<Supplier.Material, String> code = new TableColumn<>(I18n.get().t("material.code"));
        code.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().code()));
        code.setPrefWidth(100);

        TableColumn<Supplier.Material, String> name = new TableColumn<>(I18n.get().t("material.name"));
        name.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name()));
        name.setPrefWidth(230);

        TableColumn<Supplier.Material, String> price = new TableColumn<>(I18n.get().t("suppliers.unit_price"));
        price.setCellValueFactory(cell -> new SimpleStringProperty(
                Formatters.currency(cell.getValue().purchasePrice()) + " / " + cell.getValue().unit()));
        price.setPrefWidth(130);

        items.getColumns().addAll(code, name, price);

        return items;
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
}
