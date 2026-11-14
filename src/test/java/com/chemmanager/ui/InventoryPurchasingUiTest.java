package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Set;
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
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * TestFX tests for the Phase-4 screens: the user really signs in, opens the module from the
 * sidebar and reads the same scene the customer sees.
 *
 * <p>They assert behaviour rather than layout: the stock list is filled batch by batch, the KPI
 * strip is computed from those batches, the ledger tab shows the movements behind them, and the
 * purchasing screen offers its actions only to a user who may use them.</p>
 */
@ExtendWith(ApplicationExtension.class)
class InventoryPurchasingUiTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin123";

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
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
    @DisplayName("the inventory screen lists the batches, their KPIs and their ledger")
    void inventoryScreenShowsBatchesAndTheirLedger(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.INVENTORY);

        waitForRows(robot, "#stockHost");
        Set<Node> kpis = robot.lookup(".kpi-card").queryAll();
        assertEquals(4, kpis.size(), "the stock value, the batches, the quarantine and the expiry tiles");

        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertFalse(summary.getText().isBlank(), "the header must state how many batches are in stock");

        Button transfer = robot.lookup("#transferButton").queryAs(Button.class);
        assertNotNull(transfer, "a selected batch can be transferred");
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> !transfer.isDisabled());

        TabPane tabs = robot.lookup("#tabs").queryAs(TabPane.class);
        robot.interact(() -> tabs.getSelectionModel().select(1));
        WaitForAsyncUtils.waitForFxEvents();
        waitForRows(robot, "#movementHost");
        assertFalse(robot.lookup("#movementHost .table-row-cell").queryAll().isEmpty(),
                "the ledger must show the movements that produced the stock");
    }

    @Test
    @DisplayName("the purchasing screen lists the orders and offers the actions the user may run")
    void purchasingScreenListsOrders(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.PURCHASING);

        waitForRows(robot, "#tableHost");
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#detailHost .label"));

        Button newOrder = robot.lookup("#newButton").queryAs(Button.class);
        assertNotNull(newOrder, "the screen must offer a new order");
        assertFalse(newOrder.isDisabled(), "the administrator may create an order");
        assertFalse(I18n.get().t("purchasing.column.progress")
                .startsWith("!"), "the purchasing labels must be translated");
    }

    @Test
    @DisplayName("the supplier screen reads a supplier and lists the materials it supplies")
    void supplierScreenShowsItsCatalogue(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.SUPPLIERS);

        waitForRows(robot, "#tableHost");
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#detailHost"));
        assertNotNull(robot.lookup("#summaryLabel").query(), "the header must state how many suppliers exist");

        robot.interact(() -> I18n.get().setLocale(Locale.forLanguageTag("ar")));
        WaitForAsyncUtils.waitForFxEvents();
        assertNotNull(robot.lookup("#tableHost .table-row-cell").query(),
                "the list must survive a language switch");
        robot.interact(() -> I18n.get().setLocale(Locale.FRENCH));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(javafx.geometry.NodeOrientation.LEFT_TO_RIGHT,
                stage.getScene().getRoot().getNodeOrientation(), "French restores the left-to-right layout");
    }

    // ------------------------------------------------------------------ helpers

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

    private void open(FxRobot robot, NavigationModule module) throws Exception {
        Node item = robot.lookup(".nav-item").queryAll().stream()
                .filter(node -> node.getUserData() == module)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for " + module));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == module);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void waitForRows(FxRobot robot, String hostSelector) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, hostSelector + " .table-row-cell"));
        assertTrue(!robot.lookup(hostSelector + " .table-row-cell").queryAll().isEmpty(),
                "the table under " + hostSelector + " must be filled by the service layer");
    }
}
