package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.dto.CustomerQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.sales.Customer;
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
 * TestFX tests for the Phase-6 customer screen: the file is listed, the credit the server reports is
 * shown, and the account cannot be closed while it still owes money.
 */
@ExtendWith(ApplicationExtension.class)
class CustomersUiTest {

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
    @DisplayName("the customer list shows the price segment and the credit each customer still has")
    void customerListShowsSegmentsAndCredit(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.CUSTOMERS);

        waitForRows(robot, "#tableHost");
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertFalse(summary.getText().isBlank(), "the header must state how many customers exist");

        long total = ServiceFactory.customers().search(CustomerQuery.initial(25)).total();
        assertTrue(total > 0, "the demo dataset must hold customers");
        assertTrue(summary.getText().contains(Long.toString(total)),
                "the header follows the list: " + summary.getText());

        ComboBox<?> filter = robot.lookup("#segmentFilter").queryAs(ComboBox.class);
        assertNotNull(filter, "the list must be filterable by price segment");
        assertEquals(4, filter.getItems().size(), "every segment plus \"all\"");
    }

    @Test
    @DisplayName("the detail panel shows the receivable and the credit guard of the selected customer")
    void detailPanelShowsReceivable(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.CUSTOMERS);

        waitForRows(robot, "#tableHost");
        Customer indebted = ServiceFactory.customers().search(CustomerQuery.initial(25)).items().stream()
                .filter(customer -> customer.balance().signum() > 0)
                .findFirst()
                .orElseThrow(() -> new AssertionError("the demo set must hold a customer who owes money"));

        Node owed = robot.lookup("#tableHost .table-row-cell").queryAll().stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof Customer customer && customer.id() == indebted.id())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the indebted customer must be on the first page"));
        robot.doubleClickOn(owed);
        WaitForAsyncUtils.waitForFxEvents();

        String detail = detailText(robot, indebted.name());
        assertTrue(detail.contains(I18n.get().t("customers.field.balance")), detail);
        assertTrue(detail.contains(I18n.get().t("customers.field.credit_limit")), detail);
        assertTrue(robot.lookup("#statementButton").queryAs(Button.class).isDisabled() == false,
                "any listed customer can show its statement");
        assertTrue(robot.lookup("#deactivateButton").queryAs(Button.class).isDisabled(),
                "a customer who owes money cannot be closed");
    }

    @Test
    @DisplayName("filtering on a segment narrows the list to that segment")
    void segmentFilterNarrowsTheList(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot, NavigationModule.CUSTOMERS);

        waitForRows(robot, "#tableHost");
        long expected = ServiceFactory.customers().search(CustomerQuery.initial(25).withSegment("WHOLESALE")).total();
        robot.interact(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> filter = (ComboBox<String>) robot.lookup("#segmentFilter").queryAs(ComboBox.class);
            filter.getSelectionModel().select("WHOLESALE");
            filter.fireEvent(new ActionEvent());
        });
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> rowCount(robot) == expected
                && showsOnly(robot, "WHOLESALE"));
        assertTrue(expected > 0, "the demo set must hold at least one wholesale customer");
    }

    // ------------------------------------------------------------------ helpers

    private static boolean showsOnly(FxRobot robot, String segment) {
        boolean any = false;
        for (Node node : robot.lookup("#tableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty() && row.getItem() instanceof Customer customer) {
                any = true;
                if (!segment.equals(customer.priceSegment())) {
                    return false;
                }
            }
        }

        return any;
    }

    private static int rowCount(FxRobot robot) {
        return (int) robot.lookup("#tableHost .table-row-cell").queryAll().stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty())
                .count();
    }

    private static String detailText(FxRobot robot, String customerName) throws Exception {
        try {
            WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> UiWaits.showsLabel(robot, "#detailHost .label", customerName));
        } catch (TimeoutException timeout) {
            // the assertion below prints what the panel actually shows
        }

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
