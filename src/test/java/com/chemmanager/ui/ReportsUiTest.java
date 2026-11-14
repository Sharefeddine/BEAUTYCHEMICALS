package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.localization.I18n;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.Formatters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * TestFX tests for the financial statements: the margin by product, the cash flow and the result of
 * the month, each one computed by the server and rendered as it comes.
 */
@ExtendWith(ApplicationExtension.class)
class ReportsUiTest {

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
    @DisplayName("the three statements are rendered with their totals")
    void statementsAreRendered(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !robot.lookup("#marginHost .label").queryAll().isEmpty());
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !robot.lookup("#cashflowHost .label").queryAll().isEmpty());
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !robot.lookup("#resultHost .label").queryAll().isEmpty());

        String margin = labels(robot, "#marginHost");
        String cashflow = labels(robot, "#cashflowHost");
        String result = labels(robot, "#resultHost");

        assertTrue(margin.contains(I18n.get().t("reports.margin.title")),
                "the margin statement has a title: " + margin);
        assertTrue(cashflow.contains(I18n.get().t("reports.cashflow.title")),
                "the cash-flow statement has a title: " + cashflow);
        assertTrue(result.contains(I18n.get().t("reports.result.title", "").trim()),
                "the result statement has a title: " + result);

        assertTrue(robot.lookup("#kpiRow .kpi-card").queryAll().size() >= 4,
                "revenue, margin, expenses and result are shown as indicators");
    }

    @Test
    @DisplayName("the result statement chains revenue, cost of goods and expenses")
    void resultChainsTheNumbers(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> !robot.lookup("#resultHost .label").queryAll().isEmpty());

        var statement = ServiceFactory.finance().result(java.time.YearMonth.now().toString());
        String expected = Formatters.currency(statement.result());
        assertTrue(labels(robot, "#resultHost").contains(expected),
                "the screen shows the result the service computed: " + expected);
    }

    private static String labels(FxRobot robot, String selector) {
        return robot.lookup(selector + " .label").queryAll().stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText() == null ? "" : ((Label) node).getText())
                .reduce("", (left, right) -> left + " | " + right);
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

    /**
     * The reports entry sits at the bottom of the sidebar, below the fold of the test window, so the
     * navigation is triggered directly: the screen is what this test is about, not the sidebar.
     */
    private void open(FxRobot robot) throws Exception {
        robot.interact(() -> AppContext.get().navigate(NavigationModule.REPORTS));
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.REPORTS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
