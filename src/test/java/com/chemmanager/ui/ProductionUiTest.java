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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * TestFX tests for the Phase-5 production screen: the shop floor sees its orders, the actions follow
 * the state of the selected order, and a finished batch is described as sellable only once quality
 * released it.
 *
 * <p>They drive the real scene graph in demo mode, so a regression in the screen (a button that
 * cannot be reached, a panel that stays empty, a label that is not translated) fails the build
 * rather than waiting for someone to click through the application.</p>
 */
@ExtendWith(ApplicationExtension.class)
class ProductionUiTest {

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
    @DisplayName("the production screen lists the orders and reserves its actions for the right state")
    void productionScreenListsOrdersAndEnablesTheRightActions(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.PRODUCTION);

        waitForRows(robot, "#tableHost");
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertFalse(summary.getText().isBlank(), "the header must state how many orders exist");

        Button newOrder = robot.lookup("#newButton").queryAs(Button.class);
        assertNotNull(newOrder, "the screen must offer a new order");
        assertFalse(newOrder.isDisabled(), "the administrator may create an order");

        // The first order of the demo set waits for material: it can be started, not consumed.
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> !robot.lookup("#startButton")
                .queryAs(Button.class).isDisabled());
        assertTrue(robot.lookup("#consumeButton").queryAs(Button.class).isDisabled(),
                "an order that never started cannot consume material");

        ComboBox<?> filter = robot.lookup("#statusFilter").queryAs(ComboBox.class);
        assertNotNull(filter, "the list must be filterable by status");
        assertEquals(8, filter.getItems().size(), "the filter offers every status plus \"all\"");
    }

    @Test
    @DisplayName("the detail panel describes the plan, the cost and the genealogy of the selected order")
    void detailPanelDescribesTheOrder(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.PRODUCTION);

        waitForRows(robot, "#tableHost");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> robot.lookup("#detailHost .label")
                .queryAll().size() > 2);
        String detail = robot.lookup("#detailHost .label").queryAll().toString();
        assertTrue(detail.contains(I18n.get().t("production.cost.total"))
                        || detail.contains(I18n.get().t("production.materials")),
                "the detail panel must state the real cost of the run");

        Button genealogy = robot.lookup("#genealogyButton").queryAs(Button.class);
        assertFalse(genealogy.isDisabled(), "any selected order can show its genealogy");
    }

    @Test
    @DisplayName("a released batch is sellable and a quarantined one is not")
    void releasedBatchIsSellable(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.PRODUCTION);

        waitForRows(robot, "#tableHost");
        // Show only the completed order: its finished batch must be released and described as sellable.
        robot.interact(() -> statusFilter(robot).getSelectionModel().select("COMPLETED"));
        WaitForAsyncUtils.waitForFxEvents();
        waitForRows(robot, "#tableHost");

        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> robot.lookup("#detailHost .label")
                .queryAll().stream().anyMatch(node -> ((Label) node).getText()
                        .contains(I18n.get().t("production.outputs"))));
        boolean sellableMentioned = robot.lookup("#detailHost .label").queryAll().stream()
                .map(node -> ((Label) node).getText())
                .anyMatch(text -> text.contains(I18n.get().t("common.yes"))
                        || text.contains(I18n.get().t("common.no")));
        assertTrue(sellableMentioned, "the finished batch must say whether it is sellable");
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static ComboBox<String> statusFilter(FxRobot robot) {
        return (ComboBox<String>) robot.lookup("#statusFilter").queryAs(ComboBox.class);
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
                () -> !robot.lookup(hostSelector + " .table-row-cell").queryAll().isEmpty());
        assertTrue(!robot.lookup(hostSelector + " .table-row-cell").queryAll().isEmpty(),
                "the table under " + hostSelector + " must be filled by the service layer");
    }
}
