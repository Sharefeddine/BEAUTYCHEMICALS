package com.chemmanager.controller.employees;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.chemmanager.component.Card;
import com.chemmanager.component.DataTable;
import com.chemmanager.component.FormFields;
import com.chemmanager.component.Icons;
import com.chemmanager.component.KpiCard;
import com.chemmanager.component.StatusBadge;
import com.chemmanager.component.Toasts;
import com.chemmanager.dto.EmployeeQuery;
import com.chemmanager.dto.PagedResult;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.AccountingPeriod;
import com.chemmanager.model.finance.AttendanceRecord;
import com.chemmanager.model.finance.EmployeeRecord;
import com.chemmanager.model.finance.LeaveRecord;
import com.chemmanager.model.finance.SalaryLine;
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
 * The workshop floor and its payroll: who works here, who is on leave, and what a month of salaries
 * costs. Paying a salary books the expense of the month and moves the cash account, so the treasury
 * never drifts from the payroll.
 */
public final class EmployeesController {

    @FXML private HBox kpiRow;
    @FXML private VBox tableHost;
    @FXML private VBox detailHost;
    @FXML private VBox payrollHost;
    @FXML private VBox leaveHost;
    @FXML private Label summaryLabel;
    @FXML private Label payrollLabel;
    @FXML private ComboBox<String> periodSelector;
    @FXML private Button runPayrollButton;
    @FXML private Button newButton;
    @FXML private Button refreshButton;

    private DataTable<EmployeeRecord> table;
    private EmployeeQuery query = EmployeeQuery.initial(25);
    private EmployeeRecord selected;
    private List<Treasury.Account> accounts = List.of();
    private List<AccountingPeriod> periods = List.of();

    @FXML
    private void initialize() {
        newButton.setGraphic(Icons.glyph(Icons.Icon.PLUS, 12));
        newButton.setOnAction(event -> openHireDialog());
        newButton.setDisable(!SessionContext.get().can(Permission.EMPLOYEES_EDIT));
        refreshButton.setGraphic(Icons.glyph(Icons.Icon.REFRESH, 12));
        refreshButton.setOnAction(event -> reload());

        periodSelector.setOnAction(event -> loadPayroll());
        runPayrollButton.setDisable(!SessionContext.get().can(Permission.EMPLOYEES_EDIT));
        runPayrollButton.setOnAction(event -> runPayroll());

        table = new DataTable<>(this::loadPage, I18n.get().t("employees.search_prompt"))
                .columns(
                        DataTable.Column.key(I18n.get().t("employees.column.number"), 100,
                                EmployeeRecord::number),
                        DataTable.Column.text(I18n.get().t("employees.column.name"), 190,
                                EmployeeRecord::fullName),
                        DataTable.Column.text(I18n.get().t("employees.column.department"), 130,
                                EmployeeRecord::departmentName),
                        DataTable.Column.text(I18n.get().t("employees.column.position"), 170,
                                EmployeeRecord::position),
                        DataTable.Column.date(I18n.get().t("employees.column.hired"), 105,
                                EmployeeRecord::hireDate),
                        DataTable.Column.node(I18n.get().t("employees.column.active"), 95,
                                row -> StatusBadge.active(row.active())),
                        DataTable.Column.amount(I18n.get().t("employees.column.salary"), 120,
                                EmployeeRecord::baseSalary))
                .onActivate(this::select)
                .onLoaded(page -> {
                    summaryLabel.setText(I18n.get().t("employees.summary.count", page.total()));
                    renderKpis(page);
                    if (selected == null && !page.items().isEmpty()) {
                        select(page.items().get(0));
                    }
                });
        tableHost.getChildren().setAll(table);

        showHint();
        reload();
    }

    private void reload() {
        FxTasks.run(() -> ServiceFactory.finance().treasury(), value -> accounts = value.accounts(),
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().periods(), this::renderPeriods,
                error -> Toasts.error(error.getMessage()));
        FxTasks.run(() -> ServiceFactory.finance().leaveRequests(""), this::renderLeaves,
                error -> Toasts.error(error.getMessage()));
        if (table != null) {
            table.resetToFirstPage();
        }
    }

    private PagedResult<EmployeeRecord> loadPage(String search, int page, int pageSize) {
        query = query.withSearch(search).withPage(page).withPageSize(pageSize);

        return ServiceFactory.finance().employees(query);
    }

    private void renderKpis(PagedResult<EmployeeRecord> page) {
        BigDecimal payroll = BigDecimal.ZERO;
        long active = 0;
        for (EmployeeRecord employee : page.items()) {
            if (employee.active()) {
                active++;
                payroll = payroll.add(employee.baseSalary() == null ? BigDecimal.ZERO : employee.baseSalary());
            }
        }
        kpiRow.getChildren().setAll(
                new KpiCard(Icons.Icon.EMPLOYEES, "accent-primary").title(I18n.get().t("employees.kpi.headcount"))
                        .value(Long.toString(page.total())).hint(I18n.get().t("employees.kpi.headcount_hint")),
                new KpiCard(Icons.Icon.CHECK_CIRCLE, "accent-success").title(I18n.get().t("employees.kpi.active"))
                        .value(Long.toString(active)).hint(I18n.get().t("employees.kpi.active_hint")),
                new KpiCard(Icons.Icon.FINANCE, "accent-warning").title(I18n.get().t("employees.kpi.payroll"))
                        .value(Formatters.currency(payroll))
                        .hint(I18n.get().t("employees.kpi.payroll_hint")));
    }

    private void renderPeriods(List<AccountingPeriod> value) {
        periods = value;
        String current = periodSelector.getValue();
        List<String> codes = value.stream().map(AccountingPeriod::code).toList();
        periodSelector.setItems(FXCollections.observableArrayList(codes));
        String chosen = current != null && codes.contains(current) ? current : YearMonth.now().toString();
        if (codes.contains(chosen)) {
            periodSelector.setValue(chosen);
        } else if (!codes.isEmpty()) {
            periodSelector.setValue(codes.get(codes.size() - 1));
        }
        loadPayroll();
    }

    private void loadPayroll() {
        String period = periodSelector.getValue();
        if (period == null || period.isBlank()) {
            return;
        }
        FxTasks.run(() -> ServiceFactory.finance().salaryPayments(period), this::renderPayroll,
                error -> Toasts.error(error.getMessage()));
    }

    private void renderPayroll(PagedResult<SalaryLine> page) {
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        for (SalaryLine line : page.items()) {
            BigDecimal amount = line.netAmount() == null ? BigDecimal.ZERO : line.netAmount();
            net = net.add(amount);
            if (!line.paid()) {
                unpaid = unpaid.add(amount);
            }
        }
        payrollLabel.setText(I18n.get().t("employees.payroll.summary", page.total(),
                Formatters.currency(net), Formatters.currency(unpaid)));

        VBox rows = new VBox(6);
        for (SalaryLine line : page.items()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label who = new Label(line.employee() + " · " + (line.departmentName() == null ? "—"
                    : line.departmentName()));
            who.setMinWidth(250);
            Label amount = new Label(Formatters.currency(line.netAmount()));
            amount.setMinWidth(120);
            Label state = StatusBadge.of(I18n.get().t(line.paid() ? "employees.payroll.paid"
                    : "employees.payroll.unpaid"), line.paid() ? "badge-success" : "badge-warning");
            Button pay = new Button(I18n.get().t("employees.action.pay"));
            pay.getStyleClass().addAll("btn", "btn-primary", "btn-sm");
            pay.setDisable(line.paid() || !SessionContext.get().can(Permission.FINANCE_PAYMENT));
            pay.setOnAction(event -> chooseAccount(accountId -> paySalary(line, accountId)));
            row.getChildren().addAll(who, amount, state, pay);
            rows.getChildren().add(row);
        }
        if (rows.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("employees.payroll.empty"));
            empty.getStyleClass().add("text-muted");
            rows.getChildren().add(empty);
        }
        Card card = new Card(I18n.get().t("employees.payroll.card",
                periodSelector.getValue() == null ? "" : periodSelector.getValue()),
                I18n.get().t("employees.payroll.card_hint"));
        card.body(rows);
        payrollHost.getChildren().setAll(card);
    }

    private void runPayroll() {
        String period = periodSelector.getValue();
        if (period == null || period.isBlank()) {
            Toasts.warning(I18n.get().t("employees.payroll.choose_period"));
            return;
        }
        FxTasks.run(() -> ServiceFactory.finance().runPayroll(period), created -> {
            Toasts.success(I18n.get().t(created > 0 ? "employees.payroll.created" : "employees.payroll.nothing",
                    created, period));
            loadPayroll();
        }, error -> Toasts.error(error.getMessage()));
    }

    private void paySalary(SalaryLine line, long cashAccountId) {
        FxTasks.run(() -> ServiceFactory.finance().paySalary(line.id(), cashAccountId), paid -> {
            Toasts.success(I18n.get().t("employees.toast.paid", paid.employee(),
                    Formatters.currency(paid.netAmount())));
            loadPayroll();
        }, error -> Toasts.error(error.getMessage()));
    }

    private void renderLeaves(PagedResult<LeaveRecord> page) {
        VBox rows = new VBox(6);
        for (LeaveRecord leave : page.items()) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label who = new Label(leave.employee());
            who.setMinWidth(210);
            Label window = new Label(Formatters.shortDate(leave.startDate()) + " → "
                    + Formatters.shortDate(leave.endDate()) + " · " + leave.days() + "j");
            window.setMinWidth(170);
            Label type = new Label(Labels.leaveType(leave.leaveType()));
            type.setMinWidth(110);
            Label state = StatusBadge.of(Labels.leaveStatus(leave.status()),
                    Labels.financeStatusStyle(leave.status()));
            Button approve = new Button(I18n.get().t("employees.leave.approve"));
            approve.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
            approve.setDisable(!leave.isPending() || !SessionContext.get().can(Permission.EMPLOYEES_EDIT));
            approve.setOnAction(event -> decideLeave(leave, FinanceService.Decision.APPROVE));
            Button reject = new Button(I18n.get().t("employees.leave.reject"));
            reject.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
            reject.setDisable(!leave.isPending() || !SessionContext.get().can(Permission.EMPLOYEES_EDIT));
            reject.setOnAction(event -> decideLeave(leave, FinanceService.Decision.REJECT));
            row.getChildren().addAll(who, window, type, state, approve, reject);
            rows.getChildren().add(row);
        }
        if (rows.getChildren().isEmpty()) {
            Label empty = new Label(I18n.get().t("employees.leave.empty"));
            empty.getStyleClass().add("text-muted");
            rows.getChildren().add(empty);
        }
        Card card = new Card(I18n.get().t("employees.leave.title"),
                I18n.get().t("employees.leave.card_hint"));
        card.body(rows);
        leaveHost.getChildren().setAll(card);
    }

    private void decideLeave(LeaveRecord leave, FinanceService.Decision decision) {
        FxTasks.run(() -> ServiceFactory.finance().decideLeave(leave.id(), decision), updated -> {
            Toasts.success(I18n.get().t("employees.toast.leave", Labels.leaveStatus(updated.status())));
            FxTasks.run(() -> ServiceFactory.finance().leaveRequests(""), this::renderLeaves,
                    error -> Toasts.error(error.getMessage()));
        }, error -> Toasts.error(error.getMessage()));
    }

    private void select(EmployeeRecord employee) {
        selected = employee;
        renderDetail(employee);
    }

    private void showHint() {
        Label hint = new Label(I18n.get().t("employees.detail.hint"));
        hint.getStyleClass().add("text-muted");
        hint.setWrapText(true);
        detailHost.getChildren().setAll(hint);
    }

    private void renderDetail(EmployeeRecord employee) {
        Card header = new Card(employee.fullName(), employee.number() + " · "
                + (employee.departmentName() == null ? "—" : employee.departmentName()));
        VBox facts = new VBox(5,
                fact("employees.column.position", employee.position()),
                fact("employees.column.hired", Formatters.shortDate(employee.hireDate())),
                fact("employees.column.contract", Labels.contractType(employee.contractType())),
                fact("employees.column.salary", Formatters.currency(employee.baseSalary())),
                fact("employees.column.phone", employee.phone() == null || employee.phone().isBlank() ? "—"
                        : employee.phone()),
                fact("employees.payroll.last_paid", employee.lastPaidPeriod() == null ? "—"
                        : employee.lastPaidPeriod()));

        ComboBox<String> attendance = new ComboBox<>(FXCollections.observableArrayList("PRESENT", "ABSENT",
                "LEAVE", "SICK", "HOLIDAY", "SITE"));
        attendance.getSelectionModel().selectFirst();
        attendance.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.attendanceStatus(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        Button mark = new Button(I18n.get().t("employees.action.attendance"));
        mark.getStyleClass().addAll("btn", "btn-secondary", "btn-sm");
        mark.setDisable(!SessionContext.get().can(Permission.EMPLOYEES_EDIT));
        mark.setOnAction(event -> recordAttendance(employee, attendance.getValue()));
        Button leaveButton = new Button(I18n.get().t("employees.action.leave"));
        leaveButton.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
        leaveButton.setDisable(!SessionContext.get().can(Permission.EMPLOYEES_EDIT));
        leaveButton.setOnAction(event -> openLeaveDialog(employee));
        Button deactivate = new Button(I18n.get().t("employees.action.deactivate"));
        deactivate.getStyleClass().addAll("btn", "btn-ghost", "btn-sm");
        deactivate.setDisable(!employee.active() || !SessionContext.get().can(Permission.EMPLOYEES_EDIT));
        deactivate.setOnAction(event -> deactivate(employee));

        HBox actions = new HBox(8, attendance, mark, leaveButton, deactivate);
        actions.setAlignment(Pos.CENTER_LEFT);
        facts.getChildren().add(actions);
        header.body(facts);

        VBox attendanceBox = new VBox(4);
        FxTasks.run(() -> ServiceFactory.finance().attendance(employee.id(),
                        LocalDate.now().minusDays(30), LocalDate.now()),
                rows -> {
                    if (rows.isEmpty()) {
                        Label empty = new Label(I18n.get().t("employees.attendance.empty"));
                        empty.getStyleClass().add("text-muted");
                        attendanceBox.getChildren().add(empty);
                        return;
                    }
                    for (AttendanceRecord day : rows) {
                        Label line = new Label(Formatters.shortDate(day.workDate()) + " · "
                                + Labels.attendanceStatus(day.status()));
                        line.getStyleClass().add("text-muted");
                        attendanceBox.getChildren().add(line);
                    }
                }, error -> Toasts.error(error.getMessage()));

        Label attendanceTitle = new Label(I18n.get().t("employees.attendance.title"));
        attendanceTitle.getStyleClass().add("h3");
        detailHost.getChildren().setAll(header, attendanceTitle, attendanceBox);
    }

    private Label fact(String key, String value) {
        Label label = new Label(I18n.get().t(key) + " : " + value);
        label.getStyleClass().add("text-muted");

        return label;
    }

    private void recordAttendance(EmployeeRecord employee, String status) {
        FxTasks.run(() -> ServiceFactory.finance().recordAttendance(employee.id(), LocalDate.now(), status,
                        "07:45", "17:10"),
                row -> {
                    Toasts.success(I18n.get().t("employees.toast.attendance",
                            Labels.attendanceStatus(row.status())));
                    renderDetail(employee);
                },
                error -> Toasts.error(error.getMessage()));
    }

    private void deactivate(EmployeeRecord employee) {
        FxTasks.run(() -> ServiceFactory.finance().deactivateEmployee(employee.id()), updated -> {
            Toasts.info(I18n.get().t("employees.toast.deactivated", employee.fullName()));
            selected = null;
            table.reload();
            showHint();
        }, error -> Toasts.error(error.getMessage()));
    }

    private void openLeaveDialog(EmployeeRecord employee) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("employees.leave.new"));
        dialog.initOwner(tableHost.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("ANNUAL", "SICK", "MATERNITY",
                "UNPAID", "EXCEPTIONAL"));
        type.getSelectionModel().selectFirst();
        type.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.leaveType(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        DatePicker start = new DatePicker(LocalDate.now());
        DatePicker end = new DatePicker(LocalDate.now().plusDays(1));
        TextField reason = new TextField();

        VBox box = new VBox(10,
                FormFields.fieldKey("employees.leave.type", type, null),
                FormFields.fieldKey("employees.leave.start", start, null),
                FormFields.fieldKey("employees.leave.end", end, null),
                FormFields.fieldKey("employees.leave.reason", reason, null));
        box.setPrefWidth(400);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().filter(result -> result == ButtonType.OK).ifPresent(result -> {
            if (start.getValue() == null || end.getValue() == null || end.getValue().isBefore(start.getValue())) {
                Toasts.warning(I18n.get().t("employees.leave.invalid"));
                return;
            }
            FxTasks.run(() -> ServiceFactory.finance().requestLeave(employee.id(), type.getValue(),
                            start.getValue(), end.getValue(), reason.getText()),
                    created -> {
                        Toasts.success(I18n.get().t("employees.toast.leave_requested", created.days()));
                        FxTasks.run(() -> ServiceFactory.finance().leaveRequests(""), this::renderLeaves,
                                error -> Toasts.error(error.getMessage()));
                    },
                    error -> Toasts.error(error.getMessage()));
        });
    }

    private void openHireDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.get().t("employees.new.title"));
        dialog.initOwner(tableHost.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField firstName = new TextField();
        TextField lastName = new TextField();
        TextField position = new TextField();
        TextField phone = new TextField();
        DatePicker hireDate = new DatePicker(LocalDate.now());
        ComboBox<String> contract = new ComboBox<>(FXCollections.observableArrayList("CDI", "CDD", "INTERIM",
                "STAGE"));
        contract.getSelectionModel().selectFirst();
        contract.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return Labels.contractType(value);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        TextField salary = new TextField();

        VBox box = new VBox(10,
                FormFields.fieldKey("employees.new.first_name", firstName, null),
                FormFields.fieldKey("employees.new.last_name", lastName, null),
                FormFields.fieldKey("employees.new.position", position, null),
                FormFields.fieldKey("employees.new.phone", phone, null),
                FormFields.fieldKey("employees.new.hire_date", hireDate, null),
                FormFields.fieldKey("employees.new.contract", contract, null),
                FormFields.fieldKey("employees.new.salary", salary, null));
        box.setPrefWidth(430);
        dialog.getDialogPane().setContent(box);

        dialog.showAndWait().filter(result -> result == ButtonType.OK).ifPresent(result -> {
            if (firstName.getText().isBlank() || lastName.getText().isBlank()) {
                Toasts.warning(I18n.get().t("employees.new.required"));
                return;
            }
            BigDecimal value = Formatters.parseDecimal(salary.getText());
            FinanceService.NewEmployee request = new FinanceService.NewEmployee(firstName.getText().trim(),
                    lastName.getText().trim(), 0, position.getText(), phone.getText(), hireDate.getValue(),
                    contract.getValue(), value == null ? BigDecimal.ZERO : value);
            FxTasks.run(() -> ServiceFactory.finance().createEmployee(request), created -> {
                Toasts.success(I18n.get().t("employees.toast.hired", created.number()));
                selected = created;
                table.reload();
                renderDetail(created);
            }, error -> Toasts.error(error.getMessage()));
        });
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
        dialog.setTitle(I18n.get().t("employees.pay.title"));
        dialog.initOwner(payrollHost.getScene().getWindow());
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
        VBox box = new VBox(10, FormFields.fieldKey("employees.pay.account", account, null));
        box.setPrefWidth(380);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait().filter(result -> result == ButtonType.OK).ifPresent(result -> {
            Treasury.Account chosen = account.getValue();
            if (chosen != null) {
                onChosen.accept(chosen.id());
            }
        });
    }
}
