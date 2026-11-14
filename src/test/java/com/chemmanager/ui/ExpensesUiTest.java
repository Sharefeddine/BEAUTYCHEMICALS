package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.dto.ExpenseQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.ExpenseRecord;
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
 * TestFX tests for the Phase-7 expense screen: the list, the detail with the transitions its state
 * allows, and the spend per category.
 */
@ExtendWith(ApplicationExtension.class)
class ExpensesUiTest {

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
    @DisplayName("the expense list and its category summary are filled by the service")
    void listAndSummary(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        long total = ServiceFactory.finance().expenses(ExpenseQuery.initial(25)).total();
        assertTrue(total > 0, "the demo dataset must hold expenses");
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertTrue(summary.getText().contains(Long.toString(total)),
                "the header counts the expenses: " + summary.getText());

        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> !robot.lookup("#summaryHost .label").queryAll()
                .isEmpty());
        assertTrue(robot.lookup("#summaryHost .label").queryAll().size() >= 3,
                "the summary lists the categories that were used");
        assertFalse(robot.lookup("#detailHost .label").queryAll().isEmpty(),
                "the first expense is shown as soon as the list arrives");
    }

    @Test
    @DisplayName("the pay and cancel buttons follow the state of the expense")
    void actionsFollowTheState(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        ExpenseRecord paid = ServiceFactory.finance().expenses(ExpenseQuery.initial(50)).items().stream()
                .filter(row -> "PAID".equals(row.status())).findFirst().orElseThrow();
        selectRow(robot, paid.id());
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> !robot.lookup("#detailHost .label").queryAll().isEmpty());

        Button pay = button(robot, "expenses.action.pay");
        Button cancel = button(robot, "expenses.action.cancel");
        assertTrue(pay.isDisabled(), "a paid expense cannot be paid again");
        assertTrue(cancel.isDisabled(), "a paid expense is corrected by an opposite entry, not cancelled");
    }

    @Test
    @DisplayName("a draft expense is approved from its detail card")
    void draftIsApproved(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        ExpenseRecord draft = ServiceFactory.finance().expenses(ExpenseQuery.initial(50)).items().stream()
                .filter(ExpenseRecord::isDraft).findFirst().orElseThrow();
        long draftId = draft.id();
        selectRow(robot, draftId);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> !robot.lookup("#detailHost .label").queryAll().isEmpty());

        Button approve = button(robot, "expenses.action.approve");
        assertFalse(approve.isDisabled(), "a draft can be approved by an expense officer");
        robot.clickOn(approve);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> ServiceFactory.finance().expenses(ExpenseQuery.initial(50)).items().stream()
                        .anyMatch(row -> row.id() == draftId && !row.isDraft()));
        assertTrue(detailText(robot).contains(Labels.expenseStatus("APPROVED")),
                "the detail follows the new status: " + detailText(robot));
    }

    private static void selectRow(FxRobot robot, long id) {
        Node row = robot.lookup("#tableHost .table-row-cell").queryAll().stream()
                .filter(node -> node instanceof TableRow<?> candidate && !candidate.isEmpty()
                        && candidate.getItem() instanceof ExpenseRecord record && record.id() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No row for expense " + id));
        robot.doubleClickOn(row);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Button button(FxRobot robot, String labelKey) {
        String text = I18n.get().t(labelKey);

        return robot.lookup("#detailHost .button").queryAll().stream()
                .filter(node -> node instanceof Button candidate && text.equals(candidate.getText()))
                .map(node -> (Button) node)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No button " + text + " in the detail card"));
    }

    private static String detailText(FxRobot robot) {
        return robot.lookup("#detailHost .label").queryAll().stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText() == null ? "" : ((Label) node).getText())
                .reduce("", (left, right) -> left + " | " + right);
    }

    private static void waitForRows(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty());
        assertTrue(!robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty(),
                "the table must be filled by the service layer");
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
                .filter(node -> node.getUserData() == NavigationModule.EXPENSES)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for EXPENSES"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.EXPENSES);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
