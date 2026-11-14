package com.chemmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * Real user-interface tests driven by TestFX: the robot clicks, types and reads the very same
 * scene the customer sees, on the real application (login screen, shell, modules).
 *
 * <p>They complement {@code ApplicationSmokeTest}, which only proves that every screen loads.
 * Here the behaviour is asserted: translated sign-in form, password masking toggle, credential
 * sign-in that opens the shell, sidebar navigation that switches module, and the live switch to
 * Arabic that mirrors the whole layout without a restart. CI runs them headless through Xvfb.</p>
 */
@ExtendWith(ApplicationExtension.class)
class LoginScreenUiTest {

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
    @DisplayName("the sign-in screen is translated and the password starts masked")
    void signInScreenIsTranslated(FxRobot robot) {
        TextField username = robot.lookup("#usernameField").queryAs(TextField.class);
        Button signIn = robot.lookup("#signInButton").queryAs(Button.class);
        Label title = robot.lookup("#titleLabel").queryAs(Label.class);
        PasswordField password = robot.lookup("#passwordField").queryAs(PasswordField.class);
        TextField visiblePassword = robot.lookup("#passwordVisibleField").queryAs(TextField.class);

        assertEquals(I18n.get().t("login.username"), username.getPromptText(),
                "the username field must carry the translated prompt");
        assertEquals(I18n.get().t("login.password"), password.getPromptText(),
                "the password field must carry the translated prompt");
        assertEquals(I18n.get().t("login.sign_in"), signIn.getText(),
                "the sign-in button must carry the translated label");
        assertEquals(I18n.get().t("login.title"), title.getText(),
                "the screen title must be translated");
        assertTrue(password.isVisible(), "the masked password field is the visible one at start");
        assertFalse(visiblePassword.isVisible(), "the plain-text password field starts hidden");
    }

    @Test
    @DisplayName("the eye button flips between masked and plain-text password")
    void passwordVisibilityToggleWorks(FxRobot robot) {
        PasswordField password = robot.lookup("#passwordField").queryAs(PasswordField.class);
        TextField visiblePassword = robot.lookup("#passwordVisibleField").queryAs(TextField.class);

        robot.clickOn(password);
        robot.write(PASSWORD);
        robot.clickOn("#togglePasswordButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(visiblePassword.isVisible(), "the plain-text field must become visible");
        assertFalse(password.isVisible(), "the masked field must be hidden");
        assertEquals(PASSWORD, visiblePassword.getText(), "the typed secret must be carried over");

        robot.clickOn("#togglePasswordButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(password.isVisible(), "the masked field must come back");
        assertFalse(visiblePassword.isVisible(), "the plain-text field must be hidden again");
        assertEquals(PASSWORD, password.getText(), "the secret must be preserved when masking again");
    }

    @Test
    @DisplayName("demo credentials open the application shell")
    void credentialsOpenTheShell(FxRobot robot) throws Exception {
        signIn(robot);

        assertNotNull(AppContext.get().navigation(), "the shell must install the navigation manager");
        assertEquals(NavigationModule.DASHBOARD, AppContext.get().navigation().current(),
                "the shell opens on the dashboard");

        Label pageTitle = robot.lookup("#pageTitle").queryAs(Label.class);
        assertEquals(I18n.get().t("nav.dashboard"), pageTitle.getText(),
                "the header must show the translated module title");
        assertTrue(navItems(robot).size() >= 8,
                "the sidebar must list every module the administrator may open, found: " + navItems(robot).size());
    }

    @Test
    @DisplayName("clicking a sidebar entry opens that module")
    void sidebarNavigationOpensTheClickedModule(FxRobot robot) throws Exception {
        signIn(robot);

        Node products = navItem(robot, NavigationModule.PRODUCTS);
        robot.clickOn(products);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> AppContext.get().navigation().current() == NavigationModule.PRODUCTS);

        Label pageTitle = robot.lookup("#pageTitle").queryAs(Label.class);
        assertEquals(I18n.get().t("nav.products"), pageTitle.getText(),
                "the header must follow the module the user opened");
        assertNotNull(robot.lookup(".app-content-host").query(), "the module must render inside the content host");
        assertTrue(products.getStyleClass().contains("active"), "the active entry must be highlighted");
    }

    @Test
    @DisplayName("switching to Arabic mirrors the shell without restarting")
    void switchingToArabicMirrorsTheLayout(FxRobot robot) throws Exception {
        signIn(robot);
        Label pageTitle = robot.lookup("#pageTitle").queryAs(Label.class);
        String frenchTitle = pageTitle.getText();

        robot.interact(() -> I18n.get().setLocale(Locale.forLanguageTag("ar")));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(NodeOrientation.RIGHT_TO_LEFT, stage.getScene().getRoot().getNodeOrientation(),
                "Arabic must mirror the whole layout");
        assertNotEquals(frenchTitle, pageTitle.getText(), "the interface must retranslate itself");
        assertEquals(I18n.get().t("nav.dashboard"), pageTitle.getText(),
                "the header must be readable in Arabic");

        robot.interact(() -> I18n.get().setLocale(Locale.FRENCH));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(NodeOrientation.LEFT_TO_RIGHT, stage.getScene().getRoot().getNodeOrientation(),
                "returning to French must restore the left-to-right layout");
        assertEquals(frenchTitle, pageTitle.getText(), "the French title must come back");
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
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> !navItems(robot).isEmpty());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Set<Node> navItems(FxRobot robot) {
        return robot.lookup(".nav-item").queryAll();
    }

    private Node navItem(FxRobot robot, NavigationModule module) {
        return navItems(robot).stream()
                .filter(node -> node.getUserData() == module)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No sidebar entry for " + module));
    }
}
