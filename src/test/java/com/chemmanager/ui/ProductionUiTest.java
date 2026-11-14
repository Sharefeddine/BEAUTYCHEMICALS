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

import javafx.event.ActionEvent;
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
        // Filter down to the completed order: its finished batch must be described as sellable, and
        // the detail panel must follow the filter instead of keeping the order it showed before.
        filterTo(robot, "COMPLETED");
        assertEquals(1, rowCount(robot), "one completed order is the demo history");

        String sellable = labelPrefix("production.output.sellable");
        String released = detailText(robot, I18n.get().t("production.outputs"));
        assertTrue(released.contains(sellable), released);
        assertTrue(released.contains(I18n.get().t("common.yes")),
                "a released finished batch is sellable: " + released);

        // A batch still waiting for quality is the opposite case: nothing unreleased may be sold.
        filterTo(robot, "QUALITY_CONTROL");
        assertEquals(1, rowCount(robot), "one order is still waiting for quality control");
        String quarantined = detailText(robot, I18n.get().t("production.outputs"));
        assertTrue(quarantined.contains(sellable), quarantined);
        assertTrue(quarantined.contains(I18n.get().t("common.no")),
                "a batch in quarantine must not be sellable: " + quarantined);
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private static ComboBox<String> statusFilter(FxRobot robot) {
        return (ComboBox<String>) robot.lookup("#statusFilter").queryAs(ComboBox.class);
    }

    /** Selects a status in the toolbar filter and fires its action, as a user pick would. */
    private static void filterTo(FxRobot robot, String status) throws Exception {
        robot.interact(() -> {
            ComboBox<String> filter = statusFilter(robot);
            filter.getSelectionModel().select(status);
            // Programmatic selection is silent: the action is what makes the toolbar reload.
            filter.fireEvent(new ActionEvent());
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> {
            Object current = statusFilter(robot).getValue();
            return status.equals(current) && !robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty();
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    /** The translated label up to its {0} placeholder, e.g. "Vendable :" or "Sellable:". */
    private static String labelPrefix(String key) {
        String pattern = I18n.get().t(key);
        int placeholder = pattern.indexOf("{0}");

        return (placeholder < 0 ? pattern : pattern.substring(0, placeholder)).trim();
    }

    private static int rowCount(FxRobot robot) {
        return robot.lookup("#tableHost .table-row-cell").queryAll().size();
    }

    /** Waits for the detail panel to reach a section and returns all of its label texts. */
    private static String detailText(FxRobot robot, String section) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> robot.lookup("#detailHost .label")
                .queryAll().stream().anyMatch(node -> node instanceof Label label
                        && label.getText() != null && label.getText().contains(section)));

        return robot.lookup("#detailHost .label").queryAll().stream()
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
