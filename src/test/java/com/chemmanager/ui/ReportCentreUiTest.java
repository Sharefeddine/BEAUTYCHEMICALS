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
import com.chemmanager.localization.I18n;
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

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * TestFX tests for the Phase-8 report centre: the catalogue, the operational reports and the
 * statements the screen keeps below them.
 */
@ExtendWith(ApplicationExtension.class)
class ReportCentreUiTest {

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
    @DisplayName("the report centre lists its eight reports and opens the sales one")
    void catalogueListsTheEightReports(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.count(robot, "#catalogueHost .card") >= 8);
        assertEquals(8, robot.lookup("#catalogueHost .card").queryAll().size(),
                "the catalogue publishes every report of the server");

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.showsLabel(robot, "#centreHost .card-title",
                        I18n.get().t("reports.centre.sales")));
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#centreHost .table-row-cell"));
        assertTrue(robot.lookup("#centreHost .table-row-cell").queryAll().size() >= 2,
                "the sales report has a month, a customer and a product table");
    }

    @Test
    @DisplayName("a catalogue card switches the report to the warehouse valuation")
    void catalogueCardOpensAnotherReport(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.count(robot, "#catalogueHost .card") >= 8);

        Node stockCard = robot.lookup("#catalogueHost .card").queryAll().stream()
                .filter(node -> node.lookup(".card-title") instanceof Label title
                        && I18n.get().t("reports.centre.stock").equals(title.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No stock card in the catalogue"));
        Button stock = (Button) stockCard.lookup(".button");
        assertNotNull(stock, "every card offers a way to open its report");
        robot.clickOn(stock);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.showsLabel(robot, "#centreHost .card-title", I18n.get().t("reports.centre.stock")));
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#centreHost .table-row-cell"));
        assertTrue(robot.lookup("#centreHost .h3").queryAll().size() >= 4,
                "the valuation publishes its four indicators");
    }

    @Test
    @DisplayName("the statements of the accountant still sit under the catalogue")
    void statementsStayOnTheScreen(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#marginHost .label"));
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> UiWaits.hasNode(robot, "#cashflowHost .label"));
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> UiWaits.hasNode(robot, "#resultHost .label"));

        assertNotNull(robot.lookup("#summaryLabel").query(), "the window is stated in the header");
        assertFalse(((Label) robot.lookup("#summaryLabel").query()).getText().isBlank());
        assertEquals(8, ServiceFactory.reportCentre().catalogue().size());
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
                .filter(node -> node.getUserData() == NavigationModule.REPORTS)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for REPORTS"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.REPORTS);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
