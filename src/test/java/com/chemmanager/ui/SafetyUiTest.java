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
import com.chemmanager.model.safety.MaterialHazard;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

/**
 * TestFX tests for the Phase-8 chemical safety screen: the register, the file of a substance, the
 * risk filter and the incident book.
 */
@ExtendWith(ApplicationExtension.class)
class SafetyUiTest {

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
    @DisplayName("the register shows its indicators, its substances and its documentation state")
    void registerShowsTheChemicalFile(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS,
                () -> UiWaits.count(robot, "#kpiRow .kpi-card") >= 5);
        assertEquals(5, robot.lookup("#kpiRow .kpi-card").queryAll().size(),
                "the register publishes five indicators");

        waitForRows(robot, "#materialsTableHost");
        Label summary = robot.lookup("#summaryLabel").queryAs(Label.class);
        assertTrue(summary.getText().contains("12"),
                "the header counts the twelve substances: " + summary.getText());

        long hazardous = 0;
        for (Node node : robot.lookup("#materialsTableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty()
                    && row.getItem() instanceof MaterialHazard material) {
                assertNotNull(material.code());
                assertFalse(material.code().isBlank());
                if (material.isHazardous()) {
                    hazardous++;
                }
            }
        }
        assertTrue(hazardous > 0, "the hazardous substances are listed first");
        assertTrue(robot.lookup("#fileHost .card").queryAll().isEmpty(),
                "the file panel stays empty until a substance is opened");
    }

    @Test
    @DisplayName("the risk filter narrows the register to the obsolete sheet")
    void riskFilterNarrowsTheRegister(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot, "#materialsTableHost");

        select(robot, "#riskFilter", "obsolete");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> {
            for (Node node : UiWaits.all(robot, "#materialsTableHost .table-row-cell")) {
                if (node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof MaterialHazard material
                        && !"MP-0003".equals(material.code())) {
                    return false;
                }
            }

            return UiWaits.hasNode(robot, "#materialsTableHost .table-row-cell");
        });
        assertTrue(rowsShown(robot, "#materialsTableHost") >= 1, "the expired sheet is still listed");

        select(robot, "#riskFilter", "missing");
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS, () -> {
            for (Node node : UiWaits.all(robot, "#materialsTableHost .table-row-cell")) {
                if (node instanceof TableRow<?> row && !row.isEmpty()
                        && row.getItem() instanceof MaterialHazard material && material.hasSheet()) {
                    return false;
                }
            }

            return UiWaits.hasNode(robot, "#materialsTableHost .table-row-cell");
        });
    }

    @Test
    @DisplayName("opening a substance shows its sheets, its instructions and its history")
    void openingASubstanceShowsItsFile(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot, "#materialsTableHost");

        TableRow<?> target = null;
        for (Node node : robot.lookup("#materialsTableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty()
                    && row.getItem() instanceof MaterialHazard material
                    && "MP-0002".equals(material.code())) {
                target = row;
            }
        }
        assertNotNull(target, "the caustic soda must be listed");
        robot.doubleClickOn(target, MouseButton.PRIMARY);

        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> UiWaits.count(robot, "#fileHost .card") >= 4);
        String file = UiWaits.all(robot, "#fileHost .label").stream()
                .filter(node -> node instanceof Label)
                .map(node -> ((Label) node).getText() == null ? "" : ((Label) node).getText())
                .reduce("", (left, right) -> left + " | " + right);
        assertTrue(file.contains("REV-2"), "the current revision is shown: " + file);
        assertTrue(file.contains("GHS05"), "the hazard pictogram is shown: " + file);
        assertTrue(file.toLowerCase(Locale.ROOT).contains("visière"),
                "the handling instructions are shown: " + file);
        assertTrue(file.contains("INC-00001"), "the incident history is shown: " + file);
    }

    @Test
    @DisplayName("the incident book lists its entries and follows the selection")
    void incidentBookFollowsTheSelection(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);

        robot.interact(() -> {
            TabPane pane = robot.lookup("#safetyTabs").queryAs(TabPane.class);
            pane.getSelectionModel().select(1);
        });
        waitForRows(robot, "#incidentsTableHost");

        Label summary = robot.lookup("#incidentSummaryLabel").queryAs(Label.class);
        assertTrue(summary.getText().contains("2"),
                "the incident summary counts the register: " + summary.getText());

        assertTrue(robot.lookup("#investigateButton").queryAs(Button.class).isDisabled(),
                "nothing is selected yet");
        assertTrue(robot.lookup("#closeIncidentButton").queryAs(Button.class).isDisabled());

        TableRow<?> open = null;
        for (Node node : robot.lookup("#incidentsTableHost .table-row-cell").queryAll()) {
            if (node instanceof TableRow<?> row && !row.isEmpty()
                    && row.getItem() instanceof com.chemmanager.model.safety.SafetyIncident incident
                    && "OPEN".equals(incident.status())) {
                open = row;
            }
        }
        assertNotNull(open, "the demo register has an open incident");
        robot.doubleClickOn(open, MouseButton.PRIMARY);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> !UiWaits.isDisabled(robot, "#investigateButton"));
        assertTrue(robot.lookup("#closeIncidentButton").queryAs(Button.class).isDisabled(),
                "an incident that was never investigated cannot be closed");
    }

    @Test
    @DisplayName("a read-only role reads the register but cannot report an incident")
    void readOnlyRoleCannotReport(FxRobot robot) throws Exception {
        signIn(robot, "store", "store123");
        open(robot);

        waitForRows(robot, "#materialsTableHost");
        Button report = robot.lookup("#reportIncidentButton").queryAs(Button.class);
        assertNotNull(report);
        assertTrue(report.isDisabled(), "the warehouse manager may read, not report");
        assertTrue(robot.lookup("#exportButton").queryAs(Button.class) != null);
    }

    @Test
    @DisplayName("the register is still readable after switching to Arabic")
    void registerSurvivesTheArabicLayout(FxRobot robot) throws Exception {
        signIn(robot);
        open(robot);
        waitForRows(robot, "#materialsTableHost");

        robot.interact(() -> I18n.get().setLocale(Locale.forLanguageTag("ar")));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(javafx.geometry.NodeOrientation.RIGHT_TO_LEFT,
                robot.lookup("#safetyTabs").query().getScene().getRoot().getNodeOrientation(),
                "Arabic mirrors the screen");

        Tab register = robot.lookup("#safetyTabs").queryAs(TabPane.class).getTabs().get(0);
        assertEquals(I18n.get().t("safety.tab.register"), register.getText());
        robot.interact(() -> I18n.get().setLocale(Locale.FRENCH));
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

    private static long rowsShown(FxRobot robot, String host) {
        return robot.lookup(host + " .table-row-cell").queryAll().stream()
                .filter(node -> node instanceof TableRow<?> row && !row.isEmpty()).count();
    }

    private static void waitForRows(FxRobot robot, String host) throws Exception {
        WaitForAsyncUtils.waitFor(25, TimeUnit.SECONDS, () -> UiWaits.hasNode(robot, host + " .table-row-cell"));
        assertTrue(rowsShown(robot, host) > 0, "the table must be filled by the service layer");
    }

    private void signIn(FxRobot robot) throws Exception {
        signIn(robot, USERNAME, PASSWORD);
    }

    private void signIn(FxRobot robot, String username, String password) throws Exception {
        TextField user = robot.lookup("#usernameField").queryAs(TextField.class);
        PasswordField secret = robot.lookup("#passwordField").queryAs(PasswordField.class);

        robot.clickOn(user).push(KeyCode.CONTROL, KeyCode.A).write(username);
        robot.clickOn(secret).push(KeyCode.CONTROL, KeyCode.A).write(password);
        robot.clickOn("#signInButton");

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> AppContext.get().navigation() != null
                && AppContext.get().navigation().current() != null);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> UiWaits.hasNode(robot, ".nav-item"));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void open(FxRobot robot) throws Exception {
        Node item = robot.lookup(".nav-item").queryAll().stream()
                .filter(node -> node.getUserData() == NavigationModule.SAFETY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for SAFETY"));
        robot.clickOn(item);
        WaitForAsyncUtils.waitFor(20, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.SAFETY);
        WaitForAsyncUtils.waitForFxEvents();
    }
}
