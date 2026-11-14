package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.chemmanager.app.AppContext;
import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.dto.RouteQuery;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.distribution.DeliveryRoute;
import com.chemmanager.model.distribution.Vehicle;
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
 * TestFX tests for the Phase-6 distribution screen: tours are listed with the stops they serve, the
 * fleet is a second view of the same screen, and a tour only offers the transitions its state allows.
 */
@ExtendWith(ApplicationExtension.class)
class DistributionUiTest {

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
    @DisplayName("the tours list shows the stops served, and the fleet view lists the trucks")
    void toursAndFleetAreTwoViewsOfOneScreen(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        waitForRows(robot);
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        long total = ServiceFactory.distribution().routes(RouteQuery.initial(25)).total();
        assertTrue(total > 0, "the demo dataset must hold tours");
        assertTrue(summary.getText().contains(Long.toString(total)),
                "the header follows the list: " + summary.getText());

        String detail = detailText(robot);
        assertTrue(detail.contains(I18n.get().t("distribution.field.stops")), detail);
        assertTrue(detail.contains(I18n.get().t("distribution.detail.stops")),
                "the detail panel lists the stops of the tour: " + detail);

        selectView(robot, "fleet");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> !rowsOf(robot, Vehicle.class).isEmpty());
        assertFalse(rowsOf(robot, Vehicle.class).isEmpty(), "the fleet view must list the trucks");
        Vehicle vehicle = rowsOf(robot, Vehicle.class).get(0);
        assertTrue(detailText(robot).contains(vehicle.plate()),
                "the detail panel describes the selected vehicle");
        assertTrue(robot.lookup("#newButton").queryAs(Button.class).getText()
                .equals(I18n.get().t("distribution.action.new_vehicle")),
                "the create button follows the view");
    }

    @Test
    @DisplayName("a planned tour is loaded, a running tour is closed, and only running stops can be signed")
    void tourActionsFollowTheState(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot);

        selectStatus(robot, "PLANNED");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> showsOnly(robot, "PLANNED"));
        assertFalse(button(robot, "#loadButton").isDisabled(), "a planned tour can be loaded");
        assertTrue(button(robot, "#completeButton").isDisabled(), "a tour in the yard cannot be closed");
        assertTrue(buttonsWithText(robot, I18n.get().t("distribution.action.sign_stop")) == 0,
                "stops of a tour still in the yard cannot be signed");

        selectStatus(robot, "IN_TRANSIT");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> showsOnly(robot, "IN_TRANSIT"));
        assertFalse(button(robot, "#completeButton").isDisabled(), "a running tour can be closed");
        assertTrue(button(robot, "#loadButton").isDisabled(), "a running tour is already loaded");

        DeliveryRoute route = ServiceFactory.distribution().routes(
                RouteQuery.initial(25).withStatus("IN_TRANSIT")).items().get(0);
        long pending = route.stops().stream().filter(DeliveryRoute.Stop::pending).count();
        assertTrue(pending > 0, "the demo set must hold a running tour with a stop left");
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> buttonsWithText(robot, I18n.get().t("distribution.action.sign_stop")) == pending);
    }

    // ------------------------------------------------------------------ helpers

    private static java.util.List<Vehicle> rowsOf(FxRobot robot, Class<?> type) {
        return robot.lookup("#tableHost .table-row-cell").queryAll().stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()
                        && type.isInstance(row.getItem()))
                .map(node -> (Vehicle) ((TableRow<?>) node).getItem())
                .toList();
    }

    private static long buttonsWithText(FxRobot robot, String text) {
        return robot.lookup("#detailHost .button").queryAll().stream()
                .filter(node -> node instanceof Button button && text.equals(button.getText()))
                .count();
    }

    private static void selectView(FxRobot robot, String view) {
        robot.interact(() -> {
            @SuppressWarnings("unchecked")
            ComboBox<String> selector = (ComboBox<String>) robot.lookup("#viewSelector")
                    .queryAs(ComboBox.class);
            selector.getSelectionModel().select(view);
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
        return robot.lookup(selector).queryAs(Button.class);
    }

    private static boolean showsOnly(FxRobot robot, String status) {
        boolean any = false;
        for (Node node : robot.lookup("#tableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty()
                    && row.getItem() instanceof DeliveryRoute route) {
                any = true;
                if (!status.equals(route.status())) {
                    return false;
                }
            }
        }

        return any;
    }

    private static String detailText(FxRobot robot) {
        try {
            WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> UiWaits.hasNode(robot, "#detailHost .label"));
        } catch (TimeoutException timeout) {
            // the assertion in the calling test prints what is shown
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
                .filter(node -> node.getUserData() == NavigationModule.DISTRIBUTION)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for DISTRIBUTION"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.DISTRIBUTION);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static void waitForRows(FxRobot robot) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.hasNode(robot, "#tableHost .table-row-cell"));
        assertTrue(!robot.lookup("#tableHost .table-row-cell").queryAll().isEmpty(),
                "the table must be filled by the service layer");
    }
}
