package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.dto.CashQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.finance.AccountingPeriod;
import com.chemmanager.model.finance.CashTransaction;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.support.UiWaits;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * TestFX tests for the Phase-7 treasury screen: the balances, the calendar and the cash book.
 */
@ExtendWith(ApplicationExtension.class)
class FinanceUiTest {

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
    @DisplayName("the treasury screen shows the balances, the calendar and the cash book")
    void treasuryScreenShowsMoney(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.count(robot, "#kpiRow .kpi-card") >= 5);
        assertTrue(robot.lookup("#kpiRow .kpi-card").queryAll().size() >= 5,
                "the five treasury indicators must be rendered");

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#periodStrip .button"));
        int periods = ServiceFactory.finance().periods().size();
        assertTrue(robot.lookup("#periodStrip .button").queryAll().size() >= periods - 1,
                "every period of the calendar is offered");

        waitForRows(robot);
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        long total = ServiceFactory.finance().cashbook(CashQuery.initial(25)).total();
        assertTrue(summary.getText().contains(Long.toString(total)),
                "the header counts the movements: " + summary.getText());

        boolean anyRow = false;
        for (Node node : robot.lookup("#tableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty()
                    && row.getItem() instanceof CashTransaction movement) {
                anyRow = true;
                assertNotNull(movement.accountName());
                assertFalse(movement.direction().isBlank());
            }
        }
        assertTrue(anyRow, "the cash book must hold movements");
    }

    @Test
    @DisplayName("the direction filter narrows the cash book to outflows")
    void directionFilterNarrowsTheBook(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        select(robot, "#directionFilter", "OUT");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> {
            for (Node node : robot.lookup("#tableHost .table-row-cell").queryAll()) {
                if (node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof CashTransaction movement
                        && !"OUT".equals(movement.direction())) {
                    return false;
                }
            }

            return !robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty();
        });
        assertTrue(rowsShown(robot) > 0, "the filtered list must not be empty");
    }

    @Test
    @DisplayName("a month is closed from the calendar and reopened afterwards")
    void periodIsClosedThenReopened(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#periodStrip .button"));

        String openCode = ServiceFactory.finance().periods().stream().filter(AccountingPeriod::isOpen)
                .map(AccountingPeriod::code).findFirst().orElseThrow();
        Button close = robot.lookup("#periodStrip .button").queryAll().stream()
                .filter(node -> node instanceof Button button
                        && I18n.get().t("finance.action.close_period").equals(button.getText()))
                .map(node -> (Button) node)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No close button on the calendar"));
        assertFalse(close.isDisabled(), "a finance officer may close a month");

        robot.clickOn(close);
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> ServiceFactory.finance().periods().stream()
                .anyMatch(period -> period.code().equals(openCode) && period.isClosed()));
        assertEquals(1, ServiceFactory.finance().periods().stream()
                .filter(period -> period.code().equals(openCode) && period.isClosed()).count());

        // restore the demo state so the next screen starts from an open month
        AccountingPeriod closed = ServiceFactory.finance().periods().stream()
                .filter(period -> period.code().equals(openCode)).findFirst().orElseThrow();
        robot.interact(() -> ServiceFactory.finance().reopenPeriod(closed.id()));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> ServiceFactory.finance().periods().stream()
                .anyMatch(period -> period.code().equals(openCode) && period.isOpen()));
    }

    private static void select(FxRobot robot, String selector, String value) {
        robot.interact(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> box = (ComboBox<String>) robot.lookup(selector).queryAs(ComboBox.class);
            box.getSelectionModel().select(value);
            box.fireEvent(new ActionEvent());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static long rowsShown(FxRobot robot) {
        return robot.lookup("#tableHost .table-row-cell").queryAll().stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()).count();
    }

    private static void waitForRows(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#tableHost .table-row-cell"));
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
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> UiWaits.hasNode(robot, ".nav-item"));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void open(FxRobot robot) throws Exception {
        Node item = robot.lookup(".nav-item").queryAll().stream()
                .filter(node -> node.getUserData() == NavigationModule.FINANCE)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for FINANCE"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.FINANCE);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
