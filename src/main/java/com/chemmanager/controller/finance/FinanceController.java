package com.chemmanager.controller.finance;

import java.math.BigDecimal;
import java.util.List;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.CashQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.AccountingPeriod;
import com.chemmanager.model.finance.CashTransaction;
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
 * Treasury screen: the balance of every cash account, the accounting calendar, and the cash book.
 *
 * <p>Closing a month is the accountant's gesture, reopening it is the administrator's: the server
 * enforces both, and the screen only offers the buttons the signed-in user may press.</p>
 */
public final class FinanceController {

    @FXML private HBox kpiRow;
    @FXML private VBox periodStrip;
    @FXML private VBox tableHost;
    @FXML private Label summaryLabel;
    @FXML private ComboBox<String> directionFilter;
    @FXML private ComboBox<Treasury.Account> accountFilter;
    @FXML private Button newMovementButton;
    @FXML private Button refreshButton;

    private DataTable<CashTransaction> table;
    private CashQuery query = CashQuery.initial(25);
    private Treasury treasury = new Treasury(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO);
    private List<AccountingPeriod> periods = List.of();

    @FXML
    private void initialize() {
        newMovementButton.setGraphic(Icons.glyph(Icons.Icon.PLUS, 12));
        newMovementButton.setOnAction(event -> openMovementDialog());
        newMovementButton.setDisable(!SessionContext.get().can(Permission.FINANCE_PAYMENT));
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> reload());

        directionFilter.setItems(FXCollections.observableArrayList("", "IN", "OUT"));
        directionFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.get().t("common.all")
                        : Labels.cashDirection(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        directionFilter.getSelectionModel().selectFirst();
        directionFilter.setOnAction(event -> {
            query = query.withDirection(directionFilter.getValue());
            table.resetToFirstPage();
        });

        accountFilter.setConverter(new StringConverter<>() {
            @Override
            public String toString(Treasury.Account account) {
                return account == null ? I18n.get().t("common.all") : account.name();
            }

            @Override
            public Treasury.Account fromString(String text) {
                return null;
            }
        });
        accountFilter.setOnAction(event -> {
            Treasury.Account account = accountFilter.getValue();
            query = query.withAccount(account == null ? 0 : account.id());
            table.resetToFirstPage();
        });

        table = new DataTable<>(this::loadPage, I18n.get().t("finance.search_prompt"))
                .columns(
                        DataTable.Column.date(I18n.get().t("finance.column.date"), 105,
                                row -> row.occurredAt() == null ? null : row.occurredAt().toLocalDate()),
                        DataTable.Column.text(I18n.get().t("finance.column.account"), 165,
                                CashTransaction::accountName),
                        DataTable.Column.node(I18n.get().t("finance.column.direction"), 95,
                                row -> StatusBadge.of(Labels.cashDirection(row.direction()),
                                        row.isInflow() ? "badge-success" : "badge-danger")),
                        DataTable.Column.text(I18n.get().t("finance.column.source"), 120,
                                row -> Labels.cashSource(row.sourceType())),
                        DataTable.Column.text(I18n.get().t("finance.column.description"), 280,
                                CashTransaction::description),
                        DataTable.Column.amount(I18n.get().t("finance.column.amount"), 130,
                                CashTransaction::signedAmount))
                .onLoaded(page -> summaryLabel
                        .setText(I18n.get().t("finance.summary.cashbook", page.total())));
        tableHost.getChildren().setAll(table);

        reload();
    }

    private void reload() {
        FxTasks.run(() -> ServiceFactory.finance().treasury(), this::renderTreasury,
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().periods(), this::renderPeriods,
                error -> Toasts.error(error.getMessage()));
        if (table != null) {
            table.resetToFirstPage();
        }
    }

    private PagedResult<CashTransaction> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.finance().cashbook(query);
    }

    private void renderTreasury(Treasury value) {
        treasury = value;
        kpiRow.getChildren().setAll(
                new KpiCard(Icons.Icon.FINANCE, "accent-primary").title(I18n.get().t("finance.kpi.treasury"))
                        .value(Formatters.currency(value.balance())).hint(accountsHint(value)),
                new KpiCard(Icons.Icon.SALES, "accent-success").title(I18n.get().t("finance.kpi.income"))
                        .value(Formatters.currency(value.income())).hint(I18n.get().t("finance.kpi.income_hint")),
                new KpiCard(Icons.Icon.EXPENSES, "accent-warning").title(I18n.get().t("finance.kpi.expense"))
                        .value(Formatters.currency(value.expense())).hint(I18n.get().t("finance.kpi.expense_hint")),
                new KpiCard(Icons.Icon.CUSTOMERS, "accent-info").title(I18n.get().t("finance.kpi.receivable"))
                        .value(Formatters.currency(value.receivable()))
                        .hint(I18n.get().t("finance.kpi.receivable_hint")),
                new KpiCard(Icons.Icon.SUPPLIERS, "accent-danger").title(I18n.get().t("finance.kpi.payable"))
                        .value(Formatters.currency(value.payable())).hint(I18n.get().t("finance.kpi.payable_hint")));

        List<Treasury.Account> accounts = value.accounts();
        Treasury.Account previous = accountFilter.getValue();
        accountFilter.setItems(FXCollections.observableArrayList(accounts));
        if (previous != null) {
            accounts.stream().filter(account -> account.id() == previous.id()).findFirst()
                    .ifPresentOrElse(accountFilter.getSelectionModel()::select, () -> accountFilter
                            .getSelectionModel().selectFirst());
        } else {
            accountFilter.getSelectionModel().selectFirst();
            accountFilter.setValue(null);
        }
    }

    private String accountsHint(Treasury value) {
        StringBuilder hint = new StringBuilder();
        for (Treasury.Account account : value.accounts()) {
            if (!hint.isEmpty()) {
                hint.append(" · ");
            }
            hint.append(account.name()).append(' ').append(Formatters.currency(account.balance()));
        }

        return hint.isEmpty() ? I18n.get().t("finance.kpi.no_account") : hint.toString();
    }

    private void renderPeriods(List<AccountingPeriod> value) {
        periods = value;
        periodStrip.getChildren().setAll(periodTitle());
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        for (AccountingPeriod period : value) {
            row.getChildren().add(periodChip(period));
        }
        periodStrip.getChildren().add(row);
    }

    private Label periodTitle() {
        Label title = new Label(I18n.get().t("finance.periods.title"));
        title.getStyleClass().add("h3");

        return title;
    }

    private HBox periodChip(AccountingPeriod period) {
        Card card = new Card(period.code(), Labels.periodStatus(period.status()));
        Label state = StatusBadge.of(Labels.periodStatus(period.status()),
                Labels.financeStatusStyle(period.status()));
        Button toggle = new Button(I18n.get().t(period.isOpen() ? "finance.action.close_period"
                : "finance.action.reopen_period"));
        toggle.getStyleClass().addAll("btn", period.isOpen() ? "btn-secondary" : "btn-ghost", "btn-sm");
        toggle.setDisable(!SessionContext.get().can(Permission.FINANCE_CLOSE_PERIOD));
        toggle.setOnAction(event -> togglePeriod(period));
        VBox body = new VBox(6, state, toggle);
        card.body(body);
        HBox holder = new HBox(card);
        holder.setAlignment(Pos.CENTER_LEFT);

        return holder;
    }

    private void togglePeriod(AccountingPeriod period) {
        FxTasks.run(() -> period.isOpen() ? ServiceFactory.finance().closePeriod(period.id())
                        : ServiceFactory.finance().reopenPeriod(period.id()),
                updated -> {
                    Toasts.success(I18n.get().t(updated.isOpen() ? "finance.toast.reopened"
                            : "finance.toast.closed", updated.code()));
                    reload();
                },
                error -> Toasts.error(error.getMessage()));
    }

    private void openMovementDialog() {
        if (treasury.accounts().isEmpty()) {
            Toasts.warning(I18n.get().t("finance.toast.no_account"));
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("finance.movement.title"));
        dialog.initOwner(kpiRow.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<Treasury.Account> account = new ComboBox<>(FXCollections.observableArrayList(treasury.accounts()));
        account.getSelectionModel().selectFirst();
        account.setConverter(accountFilter.getConverter());
        ComboBox<String> direction = new ComboBox<>(FXCollections.observableArrayList("IN", "OUT"));
        direction.getSelectionModel().selectFirst();
        direction.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.cashDirection(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        TextField amount = new TextField();
        TextField description = new TextField();
        DatePicker date = new DatePicker(java.time.LocalDate.now());

        VBox box = new VBox(10,
                FormFields.fieldKey("finance.movement.account", account, null),
                FormFields.fieldKey("finance.movement.direction", direction, null),
                FormFields.fieldKey("finance.movement.amount", amount, null),
                FormFields.fieldKey("finance.movement.description", description, null),
                FormFields.fieldKey("finance.movement.date", date, null));
        box.setPrefWidth(420);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            BigDecimal value = Formatters.parseDecimal(amount.getText());
            if (value == null || value.signum() <= 0) {
                Toasts.warning(I18n.get().t("finance.movement.amount_required"));
                return;
            }
            FinanceService.NewCashMovement movement = new FinanceService.NewCashMovement(
                    account.getValue() == null ? treasury.accounts().get(0).id() : account.getValue().id(),
                    direction.getValue(), value, description.getText(), date.getValue());
            FxTasks.run(() -> ServiceFactory.finance().postCashMovement(movement),
                    posted -> {
                        Toasts.success(I18n.get().t("finance.toast.movement", Formatters.currency(posted.amount())));
                        reload();
                    },
                    error -> Toasts.error(error.getMessage()));
        });
    }
}
