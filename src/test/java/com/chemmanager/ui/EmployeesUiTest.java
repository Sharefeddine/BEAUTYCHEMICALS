package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.dto.EmployeeQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.EmployeeRecord;
import com.chemmanager.model.finance.LeaveRecord;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Labels;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * TestFX tests for the Phase-7 staff screen: the file, the payroll of the month and the leave desk.
 */
@ExtendWith(ApplicationExtension.class)
class EmployeesUiTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";

    @Start
    void start(Stage stage) {
        ServiceFactory.initialise();
        SessionContext.get().clear();
        I18n.get().setLocale(Locale.FRENCH);
        ThemeManager.apply(Theme.LIGHT);
        AppContext.get().boot(stage);
        stage.show();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @AfterEach
    void tearDown() {
        SessionContext.get().clear();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    @DisplayName("the staff list, the attendance and the leave desk are filled by the service")
    void staffScreenIsFilled(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        long total = ServiceFactory.finance().employees(EmployeeQuery.initial(25)).total();
        assertTrue(total > 0, "the demo dataset must hold employees");
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertTrue(summary.getText().contains(Long.toString(total)),
                "the header counts the staff: " + summary.getText());

        assertTrue(robot.lookup("#kpiRow .kpi-card").queryAll().size() >= 3,
                "headcount, active staff and payroll mass are shown");

        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> !robot.lookup("#leaveHost .label").queryAll().isEmpty());
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> !robot.lookup("#payrollHost .label").queryAll().isEmpty());
        assertFalse(robot.lookup("#detailHost .label").queryAll().isEmpty(),
                "the first employee is shown as soon as the list arrives");
    }

    @Test
    @DisplayName("the payroll of the month offers to pay what is unpaid")
    void payrollOffersPayment(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        @SuppressWarnings("unchecked")
        javafx.scene.control.ComboBox<String> selector =
                (javafx.scene.control.ComboBox<String>) robot.lookup("#periodSelector")
                        .queryAs(javafx.scene.control.ComboBox.class);
        String period = selector.getValue();
        assertTrue(period != null && !period.isBlank(), "a month is selected for the payroll");

        if (ServiceFactory.finance().salaryPayments(period).items().isEmpty()) {
            Button run = robot.lookup("#runPayrollButton").queryAs(Button.class);
            assertFalse(run.isDisabled(), "an employee officer may run the payroll");
            robot.clickOn(run);
        }
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !ServiceFactory.finance().salaryPayments(period).items().isEmpty());
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> !robot.lookup("#payrollHost .button").queryAll().isEmpty());

        Button pay = robot.lookup("#payrollHost .button").queryAll().stream()
                .filter(node -> node instanceof Button)
                .map(node -> (Button) node)
                .findFirst().orElseThrow();
        assertFalse(pay.isDisabled(), "a finance officer may pay a salary line");
    }

    @Test
    @DisplayName("a pending leave request is approved from the leave desk")
    void pendingLeaveIsApproved(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> ServiceFactory.finance().leaveRequests("REQUESTED").items().size() > 0);
        LeaveRecord pending = ServiceFactory.finance().leaveRequests("REQUESTED").items().get(0);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> !robot.lookup("#leaveHost .button").queryAll().isEmpty());

        String approveText = I18n.get().t("employees.leave.approve");
        Button approve = robot.lookup("#leaveHost .button").queryAll().stream()
                .filter(node -> node instanceof Button button && approveText.equals(button.getText())
                        && !button.isDisabled())
                .map(node -> (Button) node)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No pending approve button on the leave desk"));
        robot.clickOn(approve);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> ServiceFactory.finance()
                .leaveRequests("APPROVED").items().stream().anyMatch(row -> row.id() == pending.id()));
        Node state = robot.lookup("#leaveHost .badge").queryAll().stream()
                .filter(node -> node instanceof Label label
                        && Labels.leaveStatus("APPROVED").equals(label.getText()))
                .findFirst().orElse(null);
        assertTrue(state != null, "the leave desk shows the decision: " + detailText(robot));
    }

    private static String detailText(FxRobot robot) {
        return robot.lookup("#leaveHost .label").queryAll().stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText() == null ? "" : ((Label) node).getText())
                .reduce("", (left, right) -> left + " | " + right);
    }

    private static void waitForRows(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty());
        boolean any = false;
        for (Node node : robot.lookup("#tableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty()
                    && row.getItem() instanceof EmployeeRecord) {
                any = true;
            }
        }
        assertTrue(any, "the table must be filled by the service layer");
    }

    private void signIn(FxRobot robot) throws Exception {
        TextField username = robot.lookup("#usernameField").queryAs(TextField.class);
        PasswordField password = robot.lookup("#passwordField").queryAs(PasswordField.class);

        robot.clickOn(username).push(KeyCode.CONTROL, KeyCode.A).write(USERNAME);
        robot.clickOn(password).push(KeyCode.CONTROL, KeyCode.A).write(PASSWORD);
        robot.clickOn("#signInButton");

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> AppContext.get().navigation() != null
                && AppContext.get().navigation().current() != null);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> !robot.lookup(".nav-item").queryAll().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void open(FxRobot robot) throws Exception {
        Node item = robot.lookup(".nav-item").queryAll().stream()
                .filter(node -> node.getUserData() == NavigationModule.EMPLOYEES)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for EMPLOYEES"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.EMPLOYEES);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
