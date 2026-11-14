package com.chemmanager.controller.customers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.Dialogs;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.CustomerQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.sales.Customer;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.DocumentPrinter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The customer file: who buys, on what terms, and how much they still owe.
 *
 * <p>The credit figures come from the server — the limit it enforces when an order is confirmed and
 * the balance it books when an invoice is issued — so the screen is a truthful mirror of what the
 * sales module will allow.</p>
 */
public final class CustomersController {

    private static final List<String> SEGMENTS = List.of("", "RETAIL", "WHOLESALE", "SPECIAL");
    private static final List<String> TYPES = List.of("COMPANY", "INDIVIDUAL");

    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> segmentFilter;
    @FXML private Button newButton;
    @FXML private Button editButton;
    @FXML private Button statementButton;
    @FXML private Button deactivateButton;
    @FXML private Button refreshButton;

    private DataTable<Customer> table;
    private CustomerQuery query = CustomerQuery.initial(25);
    private Customer selected;

    @FXML
    private void initialize() {
        segmentFilter.setItems(FXCollections.observableArrayList(SEGMENTS));
        segmentFilter.setButtonCell(segmentCell());
        segmentFilter.setCellFactory(list -> segmentCell());
        segmentFilter.getSelectionModel().select("");
        segmentFilter.setOnAction(event -> {
            String value = segmentFilter.getValue();
            query = query.withSegment(value == null ? "" : value);
            table.resetToFirstPage();
        });

        newButton.setOnAction(event -> openDialog(null));
        editButton.setOnAction(event -> {
            if (selected != null) {
                openDialog(selected);
            }
        });
        statementButton.setOnAction(event -> {
            if (selected != null) {
                showStatement(selected);
            }
        });
        deactivateButton.setOnAction(event -> deactivate());
        refreshButton.setOnAction(event -> {
            table.resetToFirstPage();
            if (selected != null) {
                select(selected);
            }
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("customers.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("customers.column.code"), 90, Customer::code),
                        DataTable.Column.text(I18n.get().t("customers.column.name"), 220, Customer::name),
                        DataTable.Column.node(I18n.get().t("customers.column.segment"), 120,
                                customer -> StatusBadge.of(Labels.customerSegment(customer.priceSegment()),
                                        Labels.customerSegmentStyle(customer.priceSegment()))),
                        DataTable.Column.text(I18n.get().t("customers.column.wilaya"), 110,
                                customer -> customer.wilaya() == null ? "—" : customer.wilaya()),
                        DataTable.Column.text(I18n.get().t("customers.column.phone"), 140,
                                customer -> customer.phone() == null || customer.phone().isBlank()
                                        ? "—" : customer.phone()),
                        DataTable.Column.amount(I18n.get().t("customers.column.credit_limit"), 130,
                                Customer::creditLimit),
                        DataTable.Column.amount(I18n.get().t("customers.column.balance"), 130,
                                Customer::balance),
                        DataTable.Column.text(I18n.get().t("customers.column.available"), 130,
                                customer -> customer.creditCustomer()
                                        ? Formatters.currency(customer.availableCredit()) : "—"))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("customers.summary", page.total()));
                    Customer current = selected;
                    boolean stillListed = current != null
                            && page.items().stream().anyMatch(item -> item.id() == current.id());
                    if (!stillListed) {
                        if (page.items().isEmpty()) {
                            selected = null;
                            showHint();
                            updateActions(null);
                        } else {
                            select(page.items().get(0));
                        }
                    }
                });
        VBox.setVgrow(table, Priority.ALWAYS);
        tableHost.getChildren().setAll(table);

        showHint();
        updateActions(null);
        table.resetToFirstPage();
    }

    private PagedResult<Customer> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.customers().search(query);
    }

    private ListCell<String> segmentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null || item.isEmpty()
                        ? I18n.get().t("customers.filter.all") : Labels.customerSegment(item));
            }
        };
    }

    // ------------------------------------------------------------------ detail

    private void showHint() {
        Label hint = new Label(I18n.get().t("customers.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void select(Customer customer) {
        selected = customer;
        updateActions(customer);
        long requestedId = customer.id();
        FxTasks.run(() -> ServiceFactory.customers().load(requestedId), loaded -> {
            if (selected != null && selected.id() == requestedId) {
                renderDetail(loaded);
            }
        }, error -> Toasts.error(message(error)));
    }

    private void renderDetail(Customer customer) {
        selected = customer;
        updateActions(customer);

        Card card = new Card(customer.code() + " · " + customer.name(),
                Labels.customerType(customer.type()) + " · " + Labels.customerSegment(customer.priceSegment()));
        card.action(StatusBadge.of(Labels.customerSegment(customer.priceSegment()),
                Labels.customerSegmentStyle(customer.priceSegment())));
        card.body(new VBox(6,
                fact("customers.field.phone", customer.phone()),
                fact("customers.field.email", customer.email()),
                fact("customers.field.address", customer.address()),
                fact("customers.field.wilaya", customer.wilaya()),
                fact("customers.field.credit_limit", customer.creditCustomer()
                        ? Formatters.currency(customer.creditLimit()) : I18n.get().t("customers.cash_customer")),
                fact("customers.field.balance", Formatters.currency(customer.balance())),
                fact("customers.field.available", customer.creditCustomer()
                        ? Formatters.currency(customer.availableCredit()) : "—"),
                fact("customers.field.utilization", customer.creditCustomer()
                        ? customer.utilizationPercent() + " %" : "—"),
                fact("customers.field.invoices", Integer.toString(customer.invoiceCount())),
                fact("customers.field.orders", Integer.toString(customer.orderCount())),
                fact("customers.field.notes", customer.notes())));

        VBox box = new VBox(12, card);
        if (customer.balance().signum() > 0) {
            Label warn = new Label(I18n.get().t("customers.warning.balance",
                    Formatters.currency(customer.balance())));
            warn.getStyleClass().add("text-muted");
            warn.setWrapText(true);
            box.getChildren().add(StatusBadge.warning(warn.getText()));
        }
        Label hint = new Label(I18n.get().t("customers.statement_hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        box.getChildren().addAll(FormFields.sectionTitle("customers.statement"), hint);
        detailHost.getChildren().setAll(box);
    }

    private void updateActions(Customer customer) {
        boolean has = customer != null;
        editButton.setDisable(!has);
        statementButton.setDisable(!has);
        deactivateButton.setDisable(!has || !customer.active()
                || customer.balance().signum() != 0);
    }

    // ------------------------------------------------------------------ actions

    private void openDialog(Customer existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        boolean creating = existing == null;
        dialog.setTitle(I18n.get().t(creating ? "customers.new.title" : "customers.edit.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(existing == null ? "" : existing.name());
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(TYPES));
        type.getSelectionModel().select(existing == null ? "COMPANY" : existing.type());
        ComboBox<String> segment = new ComboBox<>(FXCollections.observableArrayList("RETAIL", "WHOLESALE", "SPECIAL"));
        segment.setButtonCell(segmentCell());
        segment.setCellFactory(list -> segmentCell());
        segment.getSelectionModel().select(existing == null ? "RETAIL" : existing.priceSegment());
        TextField phone = new TextField(existing == null ? "" : existing.phone());
        TextField email = new TextField(existing == null ? "" : existing.email());
        TextField address = new TextField(existing == null ? "" : existing.address());
        TextField wilaya = new TextField(existing == null ? "" : existing.wilaya());
        TextField creditLimit = new TextField(existing == null ? "0" : existing.creditLimit().toPlainString());
        TextArea notes = new TextArea(existing == null ? "" : existing.notes());
        notes.setPrefRowCount(2);
        CheckBox active = new CheckBox(I18n.get().t("customers.field.active"));
        active.setSelected(existing == null || existing.active());

        VBox content = new VBox(12,
                FormFields.fieldKey("customers.field.name", name, "customers.field.name_hint"),
                FormFields.row(FormFields.field("customers.field.type", type),
                        FormFields.field("customers.field.segment", segment)),
                FormFields.row(FormFields.field("customers.field.phone", phone),
                        FormFields.field("customers.field.email", email)),
                FormFields.row(FormFields.field("customers.field.wilaya", wilaya),
                        FormFields.fieldKey("customers.field.credit_limit", creditLimit,
                                "customers.field.credit_limit_hint")),
                FormFields.field("customers.field.address", address),
                FormFields.field("customers.field.notes", notes),
                active);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(620);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        BigDecimal limit = Formatters.parseDecimal(creditLimit.getText());
        if (name.getText() == null || name.getText().isBlank()) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }
        Customer.NewCustomer request = new Customer.NewCustomer(name.getText().trim(), type.getValue(),
                phone.getText(), email.getText(), address.getText(), wilaya.getText(),
                limit.signum() < 0 ? BigDecimal.ZERO : limit, segment.getValue(), notes.getText(),
                active.isSelected());

        FxTasks.run(() -> creating
                        ? ServiceFactory.customers().create(request)
                        : ServiceFactory.customers().update(existing.id(), request),
                saved -> {
                    Toasts.success(I18n.get().t(creating ? "customers.toast.created" : "customers.toast.updated",
                            saved.name()));
                    table.reload();
                    select(saved);
                },
                error -> Toasts.error(message(error)));
    }

    private void deactivate() {
        if (selected == null) {
            return;
        }
        Customer customer = selected;
        if (!Dialogs.confirm(window(), I18n.get().t("customers.deactivate.title"),
                I18n.get().t("customers.deactivate.confirm", customer.name()))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.customers().deactivate(customer.id()), closed -> {
            Toasts.success(I18n.get().t("customers.toast.closed", closed.name()));
            table.reload();
        }, error -> Toasts.error(message(error)));
    }

    private void showStatement(Customer customer) {
        FxTasks.run(() -> ServiceFactory.customers().statement(customer.id()), statement -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.initOwner(window());
            Dialogs.applyTheme(dialog.getDialogPane());
            dialog.setTitle(I18n.get().t("customers.statement.title", customer.name()));
            ButtonType print = new ButtonType(I18n.get().t("document.print"), ButtonBar.ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(print, ButtonType.CLOSE);

            TableView<Customer.Statement.Line> lines = new TableView<>();
            lines.setPrefHeight(320);
            lines.setItems(FXCollections.observableArrayList(statement.lines()));
            lines.setPlaceholder(new Label(I18n.get().t("customers.statement.empty")));
            lines.getColumns().setAll(List.of(
                    column("customers.statement.date", 110,
                            line -> line.documentDate() == null ? "—"
                                    : Formatters.shortDate(line.documentDate())),
                    column("customers.statement.document", 130, Customer.Statement.Line::documentNumber),
                    column("customers.statement.kind", 110,
                            line -> I18n.get().t("customers.statement.kind."
                                    + line.kind().toLowerCase(java.util.Locale.ROOT))),
                    column("customers.statement.debit", 120,
                            line -> Formatters.currency(line.debit())),
                    column("customers.statement.credit", 120,
                            line -> Formatters.currency(line.credit())),
                    column("customers.statement.balance", 130,
                            line -> Formatters.currency(line.balance()))));

            VBox content = new VBox(10, lines, new VBox(4,
                    fact("customers.statement.total_debit", Formatters.currency(statement.totalDebit())),
                    fact("customers.statement.total_credit", Formatters.currency(statement.totalCredit())),
                    fact("customers.statement.closing", Formatters.currency(statement.closingBalance()))));
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().setPrefWidth(780);

            Optional<ButtonType> answer = dialog.showAndWait();
            if (answer.isPresent() && answer.get() == print) {
                DocumentPrinter.open(window(), "statement", customer.id(),
                        I18n.get().locale().getLanguage());
            }
        }, error -> Toasts.error(message(error)));
    }

    private TableColumn<Customer.Statement.Line, String> column(String labelKey, double width,
                                                                java.util.function.Function<Customer.Statement.Line,
                                                                        String> value) {
        TableColumn<Customer.Statement.Line, String> column =
                new TableColumn<>(I18n.get().t(labelKey));
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));

        return column;
    }

    // ------------------------------------------------------------------ helpers

    private Window window() {
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
}
