package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.dto.SalesQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.sales.Invoice;
import com.chemmanager.model.sales.SalesOrder;
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
 * TestFX tests for the Phase-6 sales screen: the pipeline is listed with its progress, the buttons
 * follow the state of the selected document, and an issued invoice is frozen.
 */
@ExtendWith(ApplicationExtension.class)
class SalesUiTest {

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
    @DisplayName("the sales screen lists the orders with their delivery and invoicing progress")
    void ordersShowTheirProgress(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        waitForRows(robot);
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertFalse(summary.getText().isBlank(), "the header must state how many orders exist");

        long total = ServiceFactory.sales().orders(SalesQuery.initial(25)).total();
        assertTrue(total > 0, "the demo dataset must hold orders");
        assertTrue(summary.getText().contains(Long.toString(total)),
                "the header follows the list: " + summary.getText());

        String detail = detailText(robot);
        assertTrue(detail.contains(I18n.get().t("sales.field.delivered")), detail);
        assertTrue(detail.contains(I18n.get().t("sales.field.invoiced")), detail);
    }

    @Test
    @DisplayName("the order actions follow the state: a draft is confirmed, a confirmed order ships")
    void orderActionsFollowTheState(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        selectStatus(robot, "DRAFT");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> showsOnly(robot, SalesOrder.class, "DRAFT"));
        assertFalse(button(robot, "#confirmButton").isDisabled(), "a draft order can be confirmed");
        assertTrue(button(robot, "#deliverButton").isDisabled(), "a draft order cannot ship before confirmation");
        assertTrue(button(robot, "#invoiceButton").isDisabled(), "nothing can be invoiced before it ships");

        selectStatus(robot, "CONFIRMED");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> showsOnly(robot, SalesOrder.class, "CONFIRMED"));
        assertTrue(button(robot, "#confirmButton").isDisabled(), "a confirmed order is already confirmed");
        assertFalse(button(robot, "#deliverButton").isDisabled(), "a confirmed order is ready to ship");
    }

    @Test
    @DisplayName("an issued invoice can no longer be issued again: it takes a payment or a credit note")
    void issuedInvoiceIsFrozen(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        selectDocument(robot, "invoice");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> UiWaits.listsItem(robot, "#statusFilter", "ISSUED"));
        selectStatus(robot, "ISSUED");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> showsOnly(robot, Invoice.class, "ISSUED"));

        Invoice issued = ServiceFactory.sales().invoices(SalesQuery.initial(25).withStatus("ISSUED"))
                .items().get(0);
        assertTrue(button(robot, "#issueButton").isDisabled(), "an issued invoice is never issued twice");
        assertFalse(button(robot, "#payButton").isDisabled(), "an issued invoice can take a payment");
        assertFalse(button(robot, "#creditButton").isDisabled(), "an issued invoice is corrected by credit notes");
        assertTrue(triggered(robot, "sales.info.frozen"), "the screen must say the invoice is frozen");
        assertTrue(detailText(robot).contains(issued.invoiceNumber()),
                "the detail panel follows the selected invoice");
    }

    // ------------------------------------------------------------------ helpers

    private static void selectDocument(FxRobot robot, String document) {
        robot.interact(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> selector = (ComboBox<String>) robot.lookup("#documentSelector")
                    .queryAs(ComboBox.class);
            selector.getSelectionModel().select(document);
            selector.fireEvent(new ActionEvent());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void selectStatus(FxRobot robot, String status) {
        robot.interact(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> filter = (ComboBox<String>) robot.lookup("#statusFilter")
                    .queryAs(ComboBox.class);
            filter.getSelectionModel().select(status);
            filter.fireEvent(new ActionEvent());
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static Button button(FxRobot robot, String selector) {
        Button found = robot.lookup(selector).queryAs(Button.class);
        assertNotNull(found, "missing button " + selector);

        return found;
    }

    private static boolean triggered(FxRobot robot, String key) {
        return detailText(robot).contains(I18n.get().t(key));
    }

    private static <T> boolean showsOnly(FxRobot robot, Class<T> type, String status) {
        boolean any = false;
        for (Node node : robot.lookup("#tableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty() && type.isInstance(row.getItem())) {
                any = true;
                if (!status.equals(statusOf(row.getItem()))) {
                    return false;
                }
            }
        }

        return any;
    }

    private static String statusOf(Object row) {
        if (row instanceof SalesOrder order) {
            return order.status();
        }
        if (row instanceof Invoice invoice) {
            return invoice.status();
        }

        return null;
    }

    private static String detailText(FxRobot robot) {
        try {
            WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> UiWaits.hasNoNode(robot, "#detailHost .label"));
        } catch (TimeoutException timeout) {
            // the assertion in the calling test prints what is actually shown
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

    private void open(FxRobot robot) throws Exception {
        Node item = robot.lookup(".nav-item").queryAll().stream()
                .filter(node -> node.getUserData() == NavigationModule.SALES)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for SALES"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.SALES);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void waitForRows(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#tableHost .table-row-cell"));
        assertTrue(!robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty(),
                "the table must be filled by the service layer");
    }
}
