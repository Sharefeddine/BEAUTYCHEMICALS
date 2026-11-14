package com.chemmanager.controller.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import com.chemmanager.dto.CustomerQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.dto.ProductQuery;
import com.chemmanager.dto.SalesQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.Product;
import com.chemmanager.model.sales.CashAccount;
import com.chemmanager.model.sales.Customer;
import com.chemmanager.model.sales.DeliveryNote;
import com.chemmanager.model.sales.Invoice;
import com.chemmanager.model.sales.Quotation;
import com.chemmanager.model.sales.SalesOrder;
import com.chemmanager.service.SalesService;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.DocumentPrinter;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The commercial pipeline: quotations, orders, delivery notes and invoices on one screen.
 *
 * <p>The four lists are the same document at four moments, so the screen keeps them together: an
 * order shows how much of it has shipped and how much has been billed, a delivery note carries the
 * batch numbers that left the warehouse, and an issued invoice is frozen — the only actions left on
 * it are the ones the server allows, a payment or a credit note.</p>
 */
public final class SalesController {

    private static final String QUOTATION = "quotation";
    private static final String ORDER = "order";
    private static final String DELIVERY = "delivery";
    private static final String INVOICE = "invoice";
    private static final List<String> DOCUMENTS = List.of(QUOTATION, ORDER, DELIVERY, INVOICE);

    private static final Map<String, List<String>> STATUSES = Map.of(
            QUOTATION, List.of("DRAFT", "SENT", "ACCEPTED", "REJECTED", "EXPIRED"),
            ORDER, List.of("DRAFT", "CONFIRMED", "RESERVED", "PARTIAL", "DELIVERED", "INVOICED", "CANCELLED"),
            DELIVERY, List.of("PREPARED", "LOADED", "IN_TRANSIT", "DELIVERED", "RETURNED"),
            INVOICE, List.of("DRAFT", "ISSUED", "PARTIAL", "PAID", "CANCELLED"));

    private static final List<String> METHODS = List.of("CASH", "TRANSFER", "CHEQUE", "CARD", "CREDIT");

    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private HBox actionBar;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> documentSelector;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button newButton;
    @FXML private Button refreshButton;

    private final Map<String, DataTable<?>> tables = new LinkedHashMap<>();
    private final Map<String, List<Button>> actions = new LinkedHashMap<>();

    private DataTable<Quotation> quotationTable;
    private DataTable<SalesOrder> orderTable;
    private DataTable<DeliveryNote> deliveryTable;
    private DataTable<Invoice> invoiceTable;

    private SalesQuery query = SalesQuery.initial(25);
    private String document = ORDER;
    private Object selected;

    @FXML
    private void initialize() {
        buildTables();
        buildActions();

        documentSelector.setItems(FXCollections.observableArrayList(DOCUMENTS));
        documentSelector.setButtonCell(documentCell());
        documentSelector.setCellFactory(list -> documentCell());
        documentSelector.getSelectionModel().select(ORDER);
        documentSelector.setOnAction(event -> {
            String value = documentSelector.getValue();
            if (value != null && !value.equals(document)) {
                showDocument(value);
            }
        });

        statusFilter.setButtonCell(statusCell());
        statusFilter.setCellFactory(list -> statusCell());
        statusFilter.setOnAction(event -> {
            String value = statusFilter.getValue();
            query = query.withStatus(value == null ? "" : value).withPage(0);
            table().resetToFirstPage();
        });

        newButton.setOnAction(event -> {
            if (ORDER.equals(document)) {
                newOrder();
            } else {
                newQuotation();
            }
        });
        refreshButton.setOnAction(event -> reload());

        showDocument(ORDER);
    }

    // ------------------------------------------------------------------- tables

    private void buildTables() {
        quotationTable = new DataTable<>(this::loadQuotations, I18n.get().t("sales.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("sales.column.number"), 120, Quotation::quoteNumber),
                        DataTable.Column.text(I18n.get().t("sales.column.customer"), 240,
                                Quotation::customerName),
                        DataTable.Column.date(I18n.get().t("sales.column.date"), 110, Quotation::quoteDate),
                        DataTable.Column.date(I18n.get().t("sales.column.valid_until"), 120,
                                Quotation::validUntil),
                        DataTable.Column.amount(I18n.get().t("sales.column.total_ttc"), 140,
                                Quotation::totalTtc),
                        DataTable.Column.node(I18n.get().t("sales.column.status"), 130,
                                row -> badge(quotationStatus(row.status()))))
                .onActivate(this::select).onLoaded(page -> loaded(QUOTATION, page,
                        page.items().isEmpty() ? null : page.items().get(0)));

        orderTable = new DataTable<>(this::loadOrders, I18n.get().t("sales.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("sales.column.number"), 120, SalesOrder::orderNumber),
                        DataTable.Column.text(I18n.get().t("sales.column.customer"), 230,
                                SalesOrder::customerName),
                        DataTable.Column.date(I18n.get().t("sales.column.date"), 105, SalesOrder::orderDate),
                        DataTable.Column.date(I18n.get().t("sales.column.delivery_date"), 120,
                                SalesOrder::deliveryDate),
                        DataTable.Column.amount(I18n.get().t("sales.column.total_ttc"), 135,
                                SalesOrder::totalTtc),
                        DataTable.Column.text(I18n.get().t("sales.column.delivered"), 110,
                                row -> progress(row.deliveryProgress())),
                        DataTable.Column.text(I18n.get().t("sales.column.invoiced"), 110,
                                row -> progress(row.invoiceProgress())),
                        DataTable.Column.node(I18n.get().t("sales.column.status"), 120,
                                row -> badge(orderStatus(row.status()))))
                .onActivate(this::select).onLoaded(page -> loaded(ORDER, page,
                        page.items().isEmpty() ? null : page.items().get(0)));

        deliveryTable = new DataTable<>(this::loadDeliveries, I18n.get().t("sales.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("sales.column.number"), 120,
                                DeliveryNote::noteNumber),
                        DataTable.Column.text(I18n.get().t("sales.column.order"), 120,
                                DeliveryNote::orderNumber),
                        DataTable.Column.text(I18n.get().t("sales.column.customer"), 230,
                                DeliveryNote::customerName),
                        DataTable.Column.date(I18n.get().t("sales.column.prepared_at"), 115,
                                DeliveryNote::preparedAt),
                        DataTable.Column.date(I18n.get().t("sales.column.delivered_at"), 115,
                                DeliveryNote::deliveredAt),
                        DataTable.Column.quantity(I18n.get().t("sales.column.quantity"), 105,
                                row -> Formatters.quantity(row.totalQuantity(), "")),
                        DataTable.Column.node(I18n.get().t("sales.column.status"), 130,
                                row -> badge(deliveryStatus(row.status()))))
                .onActivate(this::select).onLoaded(page -> loaded(DELIVERY, page,
                        page.items().isEmpty() ? null : page.items().get(0)));

        invoiceTable = new DataTable<>(this::loadInvoices, I18n.get().t("sales.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("sales.column.number"), 120,
                                Invoice::invoiceNumber),
                        DataTable.Column.text(I18n.get().t("sales.column.customer"), 225,
                                Invoice::customerName),
                        DataTable.Column.date(I18n.get().t("sales.column.date"), 105, Invoice::invoiceDate),
                        DataTable.Column.date(I18n.get().t("sales.column.due_date"), 110, Invoice::dueDate),
                        DataTable.Column.amount(I18n.get().t("sales.column.total_ttc"), 130,
                                Invoice::totalTtc),
                        DataTable.Column.amount(I18n.get().t("sales.column.remaining"), 125,
                                Invoice::remaining),
                        DataTable.Column.node(I18n.get().t("sales.column.status"), 135,
                                row -> badge(invoiceStatus(row.status()))))
                .onActivate(this::select).onLoaded(page -> loaded(INVOICE, page,
                        page.items().isEmpty() ? null : page.items().get(0)));

        tables.put(QUOTATION, quotationTable);
        tables.put(ORDER, orderTable);
        tables.put(DELIVERY, deliveryTable);
        tables.put(INVOICE, invoiceTable);

        for (DataTable<?> table : tables.values()) {
            VBox.setVgrow(table, Priority.ALWAYS);
            table.setVisible(false);
            table.setManaged(false);
        }
        tableHost.getChildren().setAll(tables.values());
    }

    private void buildActions() {
        actions.put(QUOTATION, new ArrayList<>(List.of(
                action(QUOTATION, "sendButton", "sales.action.send", this::sendQuotation),
                action(QUOTATION, "acceptButton", "sales.action.accept", this::acceptQuotation),
                action(QUOTATION, "rejectButton", "sales.action.reject", this::rejectQuotation),
                action(QUOTATION, "printButton", "sales.action.print", () -> print(QUOTATION)))));
        actions.put(ORDER, new ArrayList<>(List.of(
                action(ORDER, "confirmButton", "sales.action.confirm", this::confirmOrder),
                action(ORDER, "deliverButton", "sales.action.deliver", this::deliver),
                action(ORDER, "invoiceButton", "sales.action.invoice", this::invoice),
                action(ORDER, "cancelOrderButton", "sales.action.cancel", this::cancelOrder),
                action(ORDER, "printButton", "sales.action.print", () -> print(ORDER)))));
        actions.put(DELIVERY, new ArrayList<>(List.of(
                action(DELIVERY, "loadButton", "sales.action.load", this::loadDelivery),
                action(DELIVERY, "signButton", "sales.action.sign", this::signDelivery),
                action(DELIVERY, "printButton", "sales.action.print", () -> print(DELIVERY)))));
        actions.put(INVOICE, new ArrayList<>(List.of(
                action(INVOICE, "issueButton", "sales.action.issue", this::issueInvoice),
                action(INVOICE, "payButton", "sales.action.pay", this::pay),
                action(INVOICE, "creditButton", "sales.action.credit", this::creditNote),
                action(INVOICE, "cancelInvoiceButton", "sales.action.cancel", this::cancelInvoice),
                action(INVOICE, "printButton", "sales.action.print", () -> print(INVOICE)))));
    }

    private Button action(String doc, String id, String key, Runnable handler) {
        Button button = new Button(I18n.get().t(key));
        button.setId(id);
        button.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        button.setDisable(true);
        button.setOnAction(event -> handler.run());

        return button;
    }

    private DataTable<?> table() {
        return tables.get(document);
    }

    private void showDocument(String value) {
        document = value;
        selected = null;
        query = query.withStatus("").withPage(0);
        tableHost.getChildren().forEach(node -> {
            node.setVisible(false);
            node.setManaged(false);
        });
        DataTable<?> active = table();
        active.setVisible(true);
        active.setManaged(true);
        actionBar.getChildren().setAll(actions.get(document));
        newButton.setText(I18n.get().t(ORDER.equals(document) ? "sales.action.new_order"
                : "sales.action.new_quotation"));
        newButton.setDisable(!ORDER.equals(document) && !QUOTATION.equals(document));

        List<String> statuses = new ArrayList<>();
        statuses.add("");
        statuses.addAll(STATUSES.get(document));
        statusFilter.setItems(FXCollections.observableArrayList(statuses));
        statusFilter.getSelectionModel().select("");

        showHint();
        updateActions();
        active.resetToFirstPage();
    }

    private void reload() {
        Object current = selected;
        selected = null;
        table().reload();
        if (current != null) {
            selected = current;
            updateActions();
        }
    }

    private void loaded(String doc, PagedResult<?> page, Object first) {
        if (!doc.equals(document)) {
            return;
        }
        summaryLabel.setText(I18n.get().t("sales.summary", page.total()));
        if (selected != null && page.items().stream()
                .anyMatch(item -> idOf(item) == idOf(selected))) {
            return;
        }
        if (first != null) {
            select(first);
        } else {
            selected = null;
            showHint();
            updateActions();
        }
    }

    // ---------------------------------------------------------------- selection

    private void select(Object row) {
        selected = row;
        updateActions();
        long id = idOf(row);
        if (row instanceof Quotation) {
            FxTasks.run(() -> ServiceFactory.sales().quotation(id),
                    loaded -> renderIfStill(QUOTATION, id, () -> renderQuotation(loaded)),
                    error -> Toasts.error(message(error)));
        } else if (row instanceof SalesOrder) {
            FxTasks.run(() -> ServiceFactory.sales().order(id),
                    loaded -> renderIfStill(ORDER, id, () -> renderOrder(loaded)),
                    error -> Toasts.error(message(error)));
        } else if (row instanceof DeliveryNote) {
            renderDelivery((DeliveryNote) row);
        } else if (row instanceof Invoice) {
            FxTasks.run(() -> ServiceFactory.sales().invoice(id),
                    loaded -> renderIfStill(INVOICE, id, () -> renderInvoice(loaded)),
                    error -> Toasts.error(message(error)));
        }
    }

    private void renderIfStill(String doc, long id, Runnable render) {
        if (doc.equals(document) && selected != null && idOf(selected) == id) {
            render.run();
        }
    }

    private void updateActions() {
        List<Button> buttons = actions.get(document);
        if (buttons == null) {
            return;
        }
        for (Button button : buttons) {
            button.setDisable(disabled(button.getId()));
        }
    }

    private boolean disabled(String id) {
        if (selected == null || id == null) {
            return true;
        }
        if (selected instanceof Quotation quotation) {
            return switch (id) {
                case "sendButton" -> !"DRAFT".equals(quotation.status());
                case "acceptButton", "rejectButton" -> !quotation.open() || "DRAFT".equals(quotation.status());
                default -> false;
            };
        }
        if (selected instanceof SalesOrder order) {
            return switch (id) {
                case "confirmButton" -> !order.confirmable();
                case "deliverButton" -> !order.deliverable();
                case "invoiceButton" -> !order.invoicable();
                case "cancelOrderButton" -> !order.cancellable();
                default -> false;
            };
        }
        if (selected instanceof DeliveryNote note) {
            return switch (id) {
                case "loadButton" -> !note.loadable();
                case "signButton" -> !note.signable();
                default -> false;
            };
        }
        if (selected instanceof Invoice invoice) {
            return switch (id) {
                case "issueButton" -> !invoice.draft();
                case "payButton" -> !invoice.payable() || invoice.remaining().signum() <= 0;
                case "creditButton" -> !invoice.creditable();
                case "cancelInvoiceButton" -> !invoice.draft();
                default -> false;
            };
        }

        return true;
    }

    // ------------------------------------------------------------------- detail

    private void showHint() {
        Label hint = new Label(I18n.get().t("sales.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderQuotation(Quotation quotation) {
        Card card = new Card(quotation.quoteNumber() + " · " + quotation.customerName(),
                I18n.get().t("sales.doc.quotation"));
        card.action(badge(quotationStatus(quotation.status())));
        card.body(new VBox(6,
                fact("sales.field.date", date(quotation.quoteDate())),
                fact("sales.field.valid_until", date(quotation.validUntil())),
                fact("sales.field.total_ht", Formatters.currency(quotation.amountHt())),
                fact("sales.field.vat", Formatters.currency(quotation.vatAmount())),
                fact("sales.field.total_ttc", Formatters.currency(quotation.totalTtc())),
                fact("sales.field.notes", quotation.notes())));
        detailHost.getChildren().setAll(card, lines("sales.detail.items", quotation.items().stream()
                .map(line -> line.productCode() + " · " + line.productName() + " — "
                        + Formatters.quantity(line.quantity(), line.unit()) + " × "
                        + Formatters.currency(line.unitPrice()) + " = "
                        + Formatters.currency(line.lineTotal()))
                .toList()));
    }

    private void renderOrder(SalesOrder order) {
        Card card = new Card(order.orderNumber() + " · " + order.customerName(),
                (order.customerCode() == null || order.customerCode().isBlank() ? "—" : order.customerCode())
                        + " · " + I18n.get().t("sales.doc.order"));
        card.action(badge(orderStatus(order.status())));
        card.body(new VBox(6,
                fact("sales.field.date", date(order.orderDate())),
                fact("sales.field.delivery_date", date(order.deliveryDate())),
                fact("sales.field.total_ttc", Formatters.currency(order.totalTtc())),
                fact("sales.field.ordered", Formatters.quantity(order.orderedQty(), "")),
                fact("sales.field.delivered_qty", Formatters.quantity(order.deliveredQty(), "")),
                fact("sales.field.delivered", progress(order.deliveryProgress())),
                fact("sales.field.invoiced_qty", Formatters.quantity(order.invoicedQty(), "")),
                fact("sales.field.invoiced", progress(order.invoiceProgress())),
                fact("sales.field.notes", order.notes())));

        VBox box = new VBox(10, card,
                lines("sales.detail.items", order.items().stream()
                        .map(line -> line.productCode() + " · " + line.productName() + " — "
                                + I18n.get().t("sales.line.progress",
                                Formatters.quantity(line.deliveredQty(), line.unit()),
                                Formatters.quantity(line.quantity(), line.unit())))
                        .toList()),
                refs("sales.detail.deliveries", order.deliveries()),
                refs("sales.detail.invoices", order.invoices()));
        detailHost.getChildren().setAll(box);
    }

    private void renderDelivery(DeliveryNote note) {
        Card card = new Card(note.noteNumber() + " · " + note.customerName(),
                I18n.get().t("sales.field.order_number") + " " + note.orderNumber());
        card.action(badge(deliveryStatus(note.status())));
        card.body(new VBox(6,
                fact("sales.field.prepared_at", date(note.preparedAt())),
                fact("sales.field.delivered_at", date(note.deliveredAt())),
                fact("sales.field.signed_by", note.signedBy()),
                fact("sales.field.route", note.routeNumber()),
                fact("sales.field.quantity", Formatters.quantity(note.totalQuantity(), "")),
                fact("sales.field.notes", note.notes())));
        detailHost.getChildren().setAll(card, lines("sales.detail.batches", note.items().stream()
                .map(line -> line.productCode() + " · " + line.productName() + " — "
                        + I18n.get().t("sales.field.batch") + " "
                        + (line.batchNumber() == null || line.batchNumber().isBlank()
                                ? "—" : line.batchNumber())
                        + " · " + Formatters.quantity(line.quantity(), line.unit()))
                .toList()));
    }

    private void renderInvoice(Invoice invoice) {
        Card card = new Card(invoice.invoiceNumber() + " · " + invoice.customerName(),
                I18n.get().t("sales.field.order_number") + " "
                        + (invoice.orderNumber() == null ? "—" : invoice.orderNumber()));
        card.action(badge(invoiceStatus(invoice.status())));

        VBox facts = new VBox(6,
                fact("sales.field.date", date(invoice.invoiceDate())),
                fact("sales.field.due_date", date(invoice.dueDate())),
                fact("sales.field.total_ht", Formatters.currency(invoice.amountHt())),
                fact("sales.field.vat", Formatters.currency(invoice.vatAmount())),
                fact("sales.field.total_ttc", Formatters.currency(invoice.totalTtc())),
                fact("sales.field.paid", Formatters.currency(invoice.paidAmount())),
                fact("sales.field.remaining", Formatters.currency(invoice.remaining())),
                fact("sales.field.printed", Integer.toString(invoice.printedCount())));
        card.body(facts);

        VBox box = new VBox(10, card);
        if (invoice.overdue()) {
            box.getChildren().add(StatusBadge.warning(I18n.get().t("sales.warning.overdue",
                    Formatters.currency(invoice.remaining()))));
        }
        if (invoice.finalized()) {
            Label frozen = new Label(I18n.get().t("sales.info.frozen"));
            frozen.getStyleClass().add("text-muted");
            frozen.setWrapText(true);
            box.getChildren().add(frozen);
        }
        box.getChildren().addAll(
                lines("sales.detail.items", invoice.items().stream()
                        .map(line -> line.productCode() + " · " + line.productName() + " — "
                                + Formatters.quantity(line.quantity(), line.unit())
                                + (line.batchNumber() == null || line.batchNumber().isBlank() ? ""
                                        : " · " + I18n.get().t("sales.field.batch") + " "
                                                + line.batchNumber())
                                + " · " + Formatters.currency(line.lineTotal()))
                        .toList()),
                lines("sales.detail.payments", invoice.payments().stream()
                        .map(payment -> payment.paymentNumber() + " · " + date(payment.paidAt()) + " · "
                                + Labels.paymentMethod(payment.method()) + " · "
                                + Formatters.currency(payment.amount()) + " · "
                                + (payment.cashAccountName() == null ? "—" : payment.cashAccountName()))
                        .toList()),
                lines("sales.detail.credit_notes", invoice.creditNotes().stream()
                        .map(note -> note.creditNumber() + " · " + date(note.creditDate()) + " · "
                                + Formatters.currency(note.totalTtc()) + " · "
                                + Labels.humanize(note.status()))
                        .toList()));
        detailHost.getChildren().setAll(box);
    }

    /** A titled block of read-only lines, or its "nothing here yet" note. */
    private Node lines(String titleKey, List<String> rows) {
        VBox box = new VBox(4, FormFields.sectionTitle(titleKey));
        if (rows.isEmpty()) {
            Label empty = new Label(I18n.get().t("sales.detail.empty"));
            empty.getStyleClass().add("text-muted");

            return new VBox(4, box, empty);
        }
        for (String row : rows) {
            Label label = new Label(row);
            label.getStyleClass().add("text-muted");
            label.setWrapText(true);
            box.getChildren().add(label);
        }

        return box;
    }

    private Node refs(String titleKey, List<SalesOrder.Ref> refs) {
        return lines(titleKey, refs.stream()
                .map(ref -> ref.number() + " · " + Labels.humanize(ref.status()) + " · "
                        + date(ref.date()))
                .toList());
    }

    // ------------------------------------------------------------------ actions

    private Quotation quotation() {
        return selected instanceof Quotation quotation ? quotation : null;
    }

    private SalesOrder order() {
        return selected instanceof SalesOrder order ? order : null;
    }

    private DeliveryNote delivery() {
        return selected instanceof DeliveryNote note ? note : null;
    }

    private Invoice invoice() {
        return selected instanceof Invoice invoice ? invoice : null;
    }

    private void sendQuotation() {
        Quotation current = quotation();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().sendQuotation(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.sent", saved.quoteNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void acceptQuotation() {
        Quotation current = quotation();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().acceptQuotation(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.accepted", saved.quoteNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void rejectQuotation() {
        Quotation current = quotation();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().rejectQuotation(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.rejected", saved.quoteNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void confirmOrder() {
        SalesOrder current = order();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().confirmOrder(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.confirmed", saved.orderNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void cancelOrder() {
        SalesOrder current = order();
        if (current == null) {
            return;
        }
        if (!Dialogs.confirm(window(), I18n.get().t("sales.action.cancel"),
                I18n.get().t("sales.confirm.cancel_order", current.orderNumber()))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().cancelOrder(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.cancelled", saved.orderNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void deliver() {
        SalesOrder current = order();
        if (current == null) {
            return;
        }
        List<SalesOrder.Line> open = current.items().stream()
                .filter(line -> line.remaining().signum() > 0)
                .toList();
        if (open.isEmpty()) {
            Toasts.warning(I18n.get().t("sales.warning.nothing_to_deliver"));
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("sales.dialog.delivery.title", current.orderNumber()));
        ButtonType deliver = new ButtonType(I18n.get().t("sales.action.deliver"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(deliver, ButtonType.CANCEL);

        Map<Long, TextField> fields = new LinkedHashMap<>();
        VBox content = new VBox(10);
        Label hint = new Label(I18n.get().t("sales.dialog.delivery.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        content.getChildren().add(hint);
        for (SalesOrder.Line line : open) {
            TextField quantity = new TextField(line.remaining().toPlainString());
            quantity.setPrefWidth(100);
            fields.put(line.id(), quantity);
            Label name = new Label(line.productCode() + " · " + line.productName());
            name.getStyleClass().add("text-muted");
            HBox row = new HBox(10, name, quantity);
            HBox.setHgrow(name, Priority.ALWAYS);
            content.getChildren().add(row);
        }
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != deliver) {
            return;
        }
        List<SalesService.DeliveryLine> lines = new ArrayList<>();
        for (SalesOrder.Line line : open) {
            BigDecimal quantity = Formatters.parseDecimal(fields.get(line.id()).getText());
            if (quantity.signum() <= 0) {
                continue;
            }
            if (quantity.compareTo(line.remaining()) > 0) {
                Toasts.error(I18n.get().t("sales.warning.exceeds_remaining", line.productName(),
                        Formatters.quantity(line.remaining(), line.unit())));
                return;
            }
            lines.add(new SalesService.DeliveryLine(line.id(), quantity));
        }
        if (lines.isEmpty()) {
            Toasts.warning(I18n.get().t("sales.warning.nothing_to_deliver"));
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().createDelivery(current.id(), lines), note -> {
            Toasts.success(I18n.get().t("sales.toast.delivery_created", note.noteNumber(),
                    note.totalQuantity().toPlainString()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void invoice() {
        SalesOrder current = order();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().createInvoice(current.id()), created -> {
            Toasts.success(I18n.get().t("sales.toast.invoiced", created.invoiceNumber()));
            documentSelector.getSelectionModel().select(INVOICE);
            showDocument(INVOICE);
            select(created);
        }, error -> Toasts.error(message(error)));
    }

    private void loadDelivery() {
        DeliveryNote current = delivery();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().loadDelivery(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.loaded", saved.noteNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void signDelivery() {
        DeliveryNote current = delivery();
        if (current == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("sales.dialog.sign.title", current.noteNumber()));
        ButtonType sign = new ButtonType(I18n.get().t("sales.action.sign"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(sign, ButtonType.CANCEL);

        TextField signedBy = new TextField(current.signedBy());
        TextArea notes = new TextArea(current.notes());
        notes.setPrefRowCount(2);
        VBox content = new VBox(12,
                FormFields.fieldKey("sales.field.signed_by", signedBy, "sales.dialog.sign.hint"),
                FormFields.field("sales.field.notes", notes));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(520);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != sign) {
            return;
        }
        if (signedBy.getText() == null || signedBy.getText().isBlank()) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().signDelivery(current.id(), signedBy.getText().trim(),
                notes.getText()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.signed", saved.noteNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void issueInvoice() {
        Invoice current = invoice();
        if (current == null) {
            return;
        }
        if (!Dialogs.confirm(window(), I18n.get().t("sales.action.issue"),
                I18n.get().t("sales.confirm.issue", current.invoiceNumber(),
                        Formatters.currency(current.totalTtc())))) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().issueInvoice(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.issued", saved.invoiceNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void cancelInvoice() {
        Invoice current = invoice();
        if (current == null) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.sales().cancelInvoice(current.id()), saved -> {
            Toasts.success(I18n.get().t("sales.toast.invoice_cancelled", saved.invoiceNumber()));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void pay() {
        Invoice current = invoice();
        if (current == null) {
            return;
        }
        List<CashAccount> accounts = ServiceFactory.sales().cashAccounts();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("sales.dialog.pay.title", current.invoiceNumber()));
        ButtonType pay = new ButtonType(I18n.get().t("sales.action.pay"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(pay, ButtonType.CANCEL);

        TextField amount = new TextField(current.remaining().toPlainString());
        ComboBox<String> method = new ComboBox<>(FXCollections.observableArrayList(METHODS));
        method.getSelectionModel().select("CASH");
        ComboBox<CashAccount> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        if (!accounts.isEmpty()) {
            account.getSelectionModel().selectFirst();
        }
        TextField reference = new TextField();

        VBox content = new VBox(12,
                new Label(I18n.get().t("sales.dialog.pay.remaining",
                        Formatters.currency(current.remaining()))),
                FormFields.fieldKey("sales.field.amount", amount, "sales.dialog.pay.hint"),
                FormFields.row(FormFields.field("sales.field.method", method),
                        FormFields.field("sales.field.account", account)),
                FormFields.field("sales.field.reference", reference));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(540);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != pay) {
            return;
        }
        BigDecimal value = Formatters.parseDecimal(amount.getText());
        if (value.signum() <= 0) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }
        if (value.compareTo(current.remaining()) > 0) {
            Toasts.error(I18n.get().t("sales.warning.exceeds_balance",
                    Formatters.currency(current.remaining())));
            return;
        }
        CashAccount chosen = account.getValue();
        SalesService.NewPayment request = new SalesService.NewPayment(current.id(), current.customerId(),
                value, chosen == null ? 0 : chosen.id(), method.getValue(), reference.getText());

        FxTasks.run(() -> ServiceFactory.sales().pay(request), payment -> {
            Toasts.success(I18n.get().t("sales.toast.paid", payment.paymentNumber(),
                    Formatters.currency(payment.amount())));
            reload();
        }, error -> Toasts.error(message(error)));
    }

    private void creditNote() {
        Invoice current = invoice();
        if (current == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t("sales.dialog.credit.title", current.invoiceNumber()));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField reason = new TextField();
        TextField amount = new TextField(current.amountHt().toPlainString());
        TextField vatRate = new TextField("19");
        VBox content = new VBox(12,
                new Label(I18n.get().t("sales.dialog.credit.hint", Formatters.currency(current.remaining()))),
                FormFields.fieldKey("sales.field.reason", reason, "sales.dialog.credit.reason_hint"),
                FormFields.row(FormFields.field("sales.field.amount_ht", amount),
                        FormFields.field("sales.field.vat_rate", vatRate)));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(540);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        BigDecimal value = Formatters.parseDecimal(amount.getText());
        if (reason.getText() == null || reason.getText().isBlank() || value.signum() <= 0) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }
        BigDecimal rate = Formatters.parseDecimal(vatRate.getText());
        FxTasks.run(() -> ServiceFactory.sales().creditNote(current.id(), reason.getText().trim(), value, rate),
                note -> {
                    Toasts.success(I18n.get().t("sales.toast.credited", note.creditNumber(),
                            Formatters.currency(note.totalTtc())));
                    reload();
                }, error -> Toasts.error(message(error)));
    }

    private void print(String type) {
        if (selected == null) {
            return;
        }
        DocumentPrinter.open(window(), type, idOf(selected), I18n.get().locale().getLanguage());
    }

    // ------------------------------------------------------------------ dialogs

    private void newOrder() {
        newDocument(true);
    }

    private void newQuotation() {
        newDocument(false);
    }

    private void newDocument(boolean order) {
        List<Customer> customers = ServiceFactory.customers().search(CustomerQuery.initial(200)).items();
        List<Product> products = ServiceFactory.products().search(ProductQuery.initial(200)).items();
        if (customers.isEmpty() || products.isEmpty()) {
            Toasts.warning(I18n.get().t("sales.warning.no_catalogue"));
            return;
        }
        Map<Long, Product> catalogue = new LinkedHashMap<>();
        for (Product product : products) {
            catalogue.put(product.id(), product);
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(window());
        Dialogs.applyTheme(dialog.getDialogPane());
        dialog.setTitle(I18n.get().t(order ? "sales.dialog.new_order.title"
                : "sales.dialog.new_quotation.title"));
        ButtonType save = new ButtonType(I18n.get().t("common.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<Customer> customer = new ComboBox<>(FXCollections.observableArrayList(customers));
        customer.getSelectionModel().selectFirst();
        customer.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.code() + " · " + item.name() + " · "
                                + Labels.customerSegment(item.priceSegment()));
            }
        });
        customer.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Customer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.code() + " · " + item.name());
            }
        });

        ComboBox<Product> product = new ComboBox<>(FXCollections.observableArrayList(products));
        product.getSelectionModel().selectFirst();
        product.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item.sku() + " · " + item.nameFr() + " · " + Formatters.currency(item.retailPrice()));
            }
        });
        product.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.sku() + " · " + item.nameFr());
            }
        });

        TextField quantity = new TextField("1");
        quantity.setPrefWidth(90);
        TextField discount = new TextField("0");
        discount.setPrefWidth(80);
        DatePicker date = new DatePicker(order ? LocalDate.now().plusDays(3) : null);
        TextArea notes = new TextArea();
        notes.setPrefRowCount(2);

        List<SalesOrder.NewLine> orderLines = new ArrayList<>();
        List<Quotation.NewLine> quoteLines = new ArrayList<>();
        ListView<String> picked = new ListView<>();
        picked.setPrefHeight(130);
        picked.setPlaceholder(new Label(I18n.get().t("sales.dialog.lines_empty")));
        Button add = new Button(I18n.get().t("sales.dialog.add_line"));
        add.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        add.setOnAction(event -> {
            Product chosen = product.getValue();
            BigDecimal amount = Formatters.parseDecimal(quantity.getText());
            if (chosen == null || amount.signum() <= 0) {
                Toasts.warning(I18n.get().t("validation.required"));
                return;
            }
            BigDecimal rebate = Formatters.parseDecimal(discount.getText());
            if (order) {
                orderLines.add(new SalesOrder.NewLine(chosen.id(), amount, rebate));
            } else {
                quoteLines.add(new Quotation.NewLine(chosen.id(), amount, chosen.retailPrice(), rebate));
            }
            picked.getItems().add(chosen.sku() + " · " + chosen.nameFr() + " × "
                    + Formatters.quantity(amount, chosen.unit()));
        });
        Button remove = new Button(I18n.get().t("sales.dialog.remove_line"));
        remove.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        remove.setOnAction(event -> {
            int index = picked.getSelectionModel().getSelectedIndex();
            if (index < 0) {
                return;
            }
            picked.getItems().remove(index);
            if (order) {
                orderLines.remove(index);
            } else {
                quoteLines.remove(index);
            }
        });

        VBox content = new VBox(12,
                FormFields.field("sales.field.customer", customer),
                FormFields.row(FormFields.field("sales.field.product", product),
                        FormFields.field("sales.field.quantity", quantity),
                        FormFields.field("sales.field.discount", discount)),
                new HBox(8, add, remove),
                picked,
                FormFields.row(FormFields.field(order ? "sales.field.delivery_date" : "sales.field.valid_until",
                        date)),
                FormFields.field("sales.field.notes", notes));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(520);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(660);

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isEmpty() || answer.get() != save) {
            return;
        }
        Customer chosenCustomer = customer.getValue();
        if (chosenCustomer == null) {
            Toasts.warning(I18n.get().t("validation.required"));
            return;
        }
        if ((order && orderLines.isEmpty()) || (!order && quoteLines.isEmpty())) {
            Toasts.warning(I18n.get().t("sales.dialog.lines_empty"));
            return;
        }
        if (order) {
            SalesOrder.NewOrder request = new SalesOrder.NewOrder(chosenCustomer.id(), List.copyOf(orderLines),
                    date.getValue(), notes.getText());
            FxTasks.run(() -> ServiceFactory.sales().createOrder(request), created -> {
                Toasts.success(I18n.get().t("sales.toast.order_created", created.orderNumber()));
                showDocument(ORDER);
                documentSelector.getSelectionModel().select(ORDER);
                select(created);
            }, error -> Toasts.error(message(error)));
        } else {
            Quotation.NewQuotation request = new Quotation.NewQuotation(chosenCustomer.id(),
                    List.copyOf(quoteLines), date.getValue(), notes.getText());
            FxTasks.run(() -> ServiceFactory.sales().createQuotation(request), created -> {
                Toasts.success(I18n.get().t("sales.toast.quotation_created", created.quoteNumber()));
                showDocument(QUOTATION);
                documentSelector.getSelectionModel().select(QUOTATION);
                select(created);
            }, error -> Toasts.error(message(error)));
        }
    }

    // ------------------------------------------------------------------ helpers

    private PagedResult<Quotation> loadQuotations(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.sales().quotations(query);
    }

    private PagedResult<SalesOrder> loadOrders(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.sales().orders(query);
    }

    private PagedResult<DeliveryNote> loadDeliveries(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.sales().deliveries(query);
    }

    private PagedResult<Invoice> loadInvoices(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.sales().invoices(query);
    }

    private ListCell<String> documentCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : I18n.get().t("sales.doc." + item));
            }
        };
    }

    private ListCell<String> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null || item.isEmpty()
                        ? I18n.get().t("sales.filter.all") : statusLabel(item));
            }
        };
    }

    private String statusLabel(String status) {
        return switch (document) {
            case QUOTATION -> quotationStatus(status);
            case DELIVERY -> deliveryStatus(status);
            case INVOICE -> invoiceStatus(status);
            default -> orderStatus(status);
        };
    }

    private static String quotationStatus(String status) {
        return Labels.quotationStatus(status);
    }

    private static String orderStatus(String status) {
        return Labels.orderStatus(status);
    }

    private static String deliveryStatus(String status) {
        return Labels.deliveryStatus(status);
    }

    private static String invoiceStatus(String status) {
        return Labels.invoiceStatus(status);
    }

    private static Node badge(String text) {
        return StatusBadge.of(text, Labels.commercialStatusStyle(text));
    }

    private static String progress(double value) {
        return Formatters.percent(BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP));
    }

    private static String date(LocalDate value) {
        return value == null ? "—" : Formatters.shortDate(value);
    }

    private static long idOf(Object row) {
        if (row instanceof Quotation quotation) {
            return quotation.id();
        }
        if (row instanceof SalesOrder order) {
            return order.id();
        }
        if (row instanceof DeliveryNote note) {
            return note.id();
        }
        if (row instanceof Invoice invoice) {
            return invoice.id();
        }

        return 0;
    }

    private Window window() {
        return table() == null || table().getScene() == null ? null : table().getScene().getWindow();
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
