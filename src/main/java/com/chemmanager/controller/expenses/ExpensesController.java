package com.chemmanager.controller.expenses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.ExpenseQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.ExpenseCategory;
import com.chemmanager.model.finance.ExpenseRecord;
import com.chemmanager.model.finance.Treasury;
import com.chemmanager.security.Permission;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.FinanceService;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;
import com.chemmanager.util.FxTasks;
import com.chemmanager.util.Labels;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * Operating expenses: the list, the detail of one expense with the transitions its state allows,
 * and where the money went per category. The payment is what writes the cash-book line.
 */
public final class ExpensesController {

    @FXML private HBox kpiRow;
    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private VBox summaryHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<ExpenseCategory> categoryFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Button newButton;
    @FXML private Button refreshButton;

    private DataTable<ExpenseRecord> table;
    private ExpenseQuery query = ExpenseQuery.initial(25);
    private ExpenseRecord selected;
    private List<ExpenseCategory> categories = List.of();
    private List<Treasury.Account> accounts = List.of();

    @FXML
    private void initialize() {
        newButton.setGraphic(Icons.glyph(Icons.Icon.PLUS, 12));
        newButton.setOnAction(event -> openEntryDialog());
        newButton.setDisable(!SessionContext.get().can(Permission.EXPENSES_CREATE));
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> reload());

        categoryFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExpenseCategory category) {
                return category == null ? I18n.get().t("common.all") : category.nameFr();
            }

            @Override
            public ExpenseCategory fromString(String text) {
                return null;
            }
        });
        categoryFilter.setOnAction(event -> {
            ExpenseCategory category = categoryFilter.getValue();
            query = query.withCategory(category == null ? 0 : category.id());
            table.resetToFirstPage();
        });

        statusFilter.setItems(FXCollections.observableArrayList("", "DRAFT", "APPROVED", "PAID", "CANCELLED"));
        statusFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all")
                        : Labels.expenseStatus(value);
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

        table = new DataTable<>(this::loadPage, I18n.get().t("expenses.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("expenses.column.number"), 105,
                                ExpenseRecord::number),
                        DataTable.Column.date(I18n.get().t("expenses.column.date"), 100,
                                ExpenseRecord::expenseDate),
                        DataTable.Column.text(I18n.get().t("expenses.column.category"), 130,
                                ExpenseRecord::categoryName),
                        DataTable.Column.text(I18n.get().t("expenses.column.label"), 260,
                                ExpenseRecord::label),
                        DataTable.Column.node(I18n.get().t("common.status"), 110,
                                row -> StatusBadge.of(Labels.expenseStatus(row.status()),
                                        Labels.financeStatusStyle(row.status()))),
                        DataTable.Column.amount(I18n.get().t("expenses.column.total"), 120,
                                ExpenseRecord::totalAmount))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("expenses.summary.count", page.total()));
                    if (selected == null && !page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        tableHost.getChildren().setAll(table);

        showHint();
        reload();
    }

    private void reload() {
        FxTasks.run(() -> ServiceFactory.finance().expenseCategories(), this::renderCategories,
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().treasury(), value -> accounts = value.accounts(),
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().expenseSummary(null, null), summary -> {
            renderSummary(summary);
            renderKpis(summary);
        }, error -> Toasts.error(error.getMessage()));
        if (table != null) {
            table.resetToFirstPage();
        }
    }

    private PagedResult<ExpenseRecord> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.finance().expenses(query);
    }

    private void renderCategories(List<ExpenseCategory> value) {
        categories = value;
        ExpenseCategory previous = categoryFilter.getValue();
        categoryFilter.setItems(FXCollections.observableArrayList(value));
        if (previous != null) {
            value.stream().filter(category -> category.id() == previous.id()).findFirst()
                    .ifPresent(categoryFilter.getSelectionModel()::select);
        }
    }

    private void renderSummary(FinanceService.ExpenseSummary summary) {
        Card card = new Card(I18n.get().t("expenses.summary.title"),
                I18n.get().t("expenses.summary.total", Formatters.currency(summary.amount())));
        VBox rows = new VBox(4);
        for (var row : summary.byCategory()) {
            Label line = new Label(row.name() + " · " + Formatters.currency(row.amount()));
            line.getStyleClass().add("text-muted");
            rows.getChildren().add(line);
        }
        if (rows.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("expenses.summary.empty"));
            empty.getStyleClass().add("text-muted");
            rows.getChildren().add(empty);
        }
        card.body(rows);
        summaryHost.getChildren().setAll(card);
    }

    private void renderKpis(FinanceService.ExpenseSummary summary) {
        kpiRow.getChildren().setAll(
                new KpiCard(Icons.Icon.EXPENSES, "accent-warning").title(I18n.get().t("expenses.kpi.total"))
                        .value(Formatters.currency(summary.amount()))
                        .hint(I18n.get().t("expenses.kpi.total_hint")),
                new KpiCard(Icons.Icon.FINANCE, "accent-primary").title(I18n.get().t("expenses.kpi.categories"))
                        .value(Integer.toString(summary.byCategory().size()))
                        .hint(I18n.get().t("expenses.kpi.categories_hint")));
    }

    private void select(ExpenseRecord expense) {
        selected = expense;
        renderDetail(expense);
    }

    private void showHint() {
        Label hint = new Label(I18n.get().t("expenses.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderDetail(ExpenseRecord expense) {
        Card header = new Card(expense.number() + " · " + expense.label(),
                expense.categoryName() + " · " + Labels.expenseStatus(expense.status()));
        VBox facts = new VBox(5,
                fact("expenses.column.date", Formatters.shortDate(expense.expenseDate())),
                fact("expenses.column.method", Labels.paymentMethod(expense.paymentMethod())),
                fact("expenses.amount_ht", Formatters.currency(expense.amount())),
                fact("expenses.vat", Formatters.currency(expense.vatAmount())),
                fact("expenses.column.total", Formatters.currency(expense.totalAmount())),
                fact("expenses.detail.creator", expense.creatorName()),
                fact("expenses.detail.approved_by",
                        expense.approvedByName() == null || expense.approvedByName().isBlank()
                                ? I18n.get().t("expenses.detail.not_approved") : expense.approvedByName()),
                fact("expenses.detail.cash_account",
                        expense.cashAccountName() == null || expense.cashAccountName().isBlank()
                                ? "—" : expense.cashAccountName()));

        Button approve = actionButton("expenses.action.approve", "btn-primary",
                expense.canBeApproved() && SessionContext.get().can(Permission.EXPENSES_CREATE),
                () -> approve(expense));
        Button pay = actionButton("expenses.action.pay", "btn-secondary",
                expense.canBePaid() && SessionContext.get().can(Permission.FINANCE_PAYMENT),
                () -> chooseAccount(accountId -> pay(expense, accountId)));
        Button cancel = actionButton("expenses.action.cancel", "btn-ghost",
                expense.canBeCancelled() && SessionContext.get().can(Permission.EXPENSES_CREATE),
                () -> cancel(expense));
        HBox actions = new HBox(8, approve, pay, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);
        facts.getChildren().add(actions);
        header.body(facts);
        detailHost.getChildren().setAll(header);
    }

    private Button actionButton(String key, String style, boolean allowed, Runnable action) {
        Button button = new Button(I18n.get().t(key));
        button.getStyleClass().addAll("btn", style, "btn-sm");
        button.setDisable(!allowed);
        button.setOnAction(event -> action.run());

        return button;
    }

    private Label fact(String key, String value) {
        Label label = new Label(I18n.get().t(key) + " : " + value);
        label.getStyleClass().add("text-muted");

        return label;
    }

    private void approve(ExpenseRecord expense) {
        FxTasks.run(() -> ServiceFactory.finance().approveExpense(expense.id()), updated -> {
            Toasts.success(I18n.get().t("expenses.toast.approved", updated.number()));
            after(updated);
        }, error -> Toasts.error(error.getMessage()));
    }

    private void pay(ExpenseRecord expense, long cashAccountId) {
        FxTasks.run(() -> ServiceFactory.finance().payExpense(expense.id(), cashAccountId), updated -> {
            Toasts.success(I18n.get().t("expenses.toast.paid", updated.number(),
                    Formatters.currency(updated.totalAmount())));
            after(updated);
        }, error -> Toasts.error(error.getMessage()));
    }

    private void cancel(ExpenseRecord expense) {
        FxTasks.run(() -> ServiceFactory.finance().cancelExpense(expense.id()), updated -> {
            Toasts.info(I18n.get().t("expenses.toast.cancelled", updated.number()));
            after(updated);
        }, error -> Toasts.error(error.getMessage()));
    }

    private void after(ExpenseRecord updated) {
        selected = updated;
        table.reload();
        renderDetail(updated);
        FxTasks.run(() -> ServiceFactory.finance().expenseSummary(null, null), summary -> {
            renderSummary(summary);
            renderKpis(summary);
        }, error -> Toasts.error(error.getMessage()));
    }

    private void chooseAccount(java.util.function.LongConsumer onChosen) {
        if (accounts.isEmpty()) {
            FxTasks.run(() -> ServiceFactory.finance().treasury(), value -> {
                accounts = value.accounts();
                chooseAccount(onChosen);
            }, error -> Toasts.error(error.getMessage()));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("expenses.pay.title"));
        dialog.initOwner(detailHost.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ComboBox<Treasury.Account> account = new ComboBox<>(FXCollections.observableArrayList(accounts));
        account.getSelectionModel().selectFirst();
        account.setConverter(new StringConverter<>() {
            @Override
            public String toString(Treasury.Account value) {
                return value == null ? "—" : value.name() + " · " + Formatters.currency(value.balance());
            }

            @Override
            public Treasury.Account fromString(String text) {
                return null;
            }
        });
        VBox box = new VBox(10, FormFields.fieldKey("expenses.pay.account", account, null));
        box.setPrefWidth(380);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait().filter(result -> result == ButtonType.OK).ifPresent(result -> {
            Treasury.Account chosen = account.getValue();
            if (chosen != null) {
                onChosen.accept(chosen.id());
            }
        });
    }

    private void openEntryDialog() {
        if (categories.isEmpty()) {
            Toasts.warning(I18n.get().t("expenses.toast.no_categories"));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("expenses.new.title"));
        dialog.initOwner(tableHost.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<ExpenseCategory> category = new ComboBox<>(FXCollections.observableArrayList(categories));
        category.getSelectionModel().selectFirst();
        category.setConverter(categoryFilter.getConverter());
        TextField label = new TextField();
        TextField amount = new TextField();
        TextField vat = new TextField("0");
        DatePicker date = new DatePicker(LocalDate.now());
        ComboBox<String> method = new ComboBox<>(FXCollections.observableArrayList("CASH", "CHEQUE", "TRANSFER",
                "CARD", "CREDIT"));
        method.getSelectionModel().selectFirst();
        method.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.paymentMethod(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        TextField notes = new TextField();

        VBox box = new VBox(10,
                FormFields.fieldKey("expenses.new.category", category, null),
                FormFields.fieldKey("expenses.new.label", label, null),
                FormFields.fieldKey("expenses.new.amount", amount, null),
                FormFields.fieldKey("expenses.new.vat", vat, null),
                FormFields.fieldKey("expenses.new.date", date, null),
                FormFields.fieldKey("expenses.new.method", method, null),
                FormFields.fieldKey("expenses.new.notes", notes, null));
        box.setPrefWidth(440);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().filter(result -> result == ButtonType.OK).ifPresent(result -> {
            BigDecimal value = Formatters.parseDecimal(amount.getText());
            if (value == null || value.signum() <= 0 || label.getText() == null || label.getText().isBlank()) {
                Toasts.warning(I18n.get().t("expenses.new.required"));
                return;
            }
            BigDecimal vatValue = Formatters.parseDecimal(vat.getText());
            FinanceService.NewExpense request = new FinanceService.NewExpense(
                    category.getValue() == null ? categories.get(0).id() : category.getValue().id(),
                    label.getText().trim(), value, vatValue == null ? BigDecimal.ZERO : vatValue,
                    date.getValue(), method.getValue(), 0, notes.getText());
            FxTasks.run(() -> ServiceFactory.finance().createExpense(request), created -> {
                Toasts.success(I18n.get().t("expenses.toast.created", created.number()));
                selected = created;
                table.reload();
                renderDetail(created);
            }, error -> Toasts.error(error.getMessage()));
        });
    }
}
