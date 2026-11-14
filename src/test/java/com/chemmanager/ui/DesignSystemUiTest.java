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
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

/**
 * Design-system behaviour on the real interface: the keyboard focus ring, the Arabic typography
 * switch and the accessible names of the icon-only header controls.
 *
 * <p>{@code DesignSystemTest} proves the tokens and the stylesheet are honest; this class proves
 * the running scene actually uses them, which is what a keyboard or a screen-reader user meets.</p>
 */
@ExtendWith(ApplicationExtension.class)
class DesignSystemUiTest {

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
    @DisplayName("a focused control paints a visible focus ring")
    void focusedControlPaintsAVisibleRing(FxRobot robot) throws Exception {
        Button signIn = robot.lookup("#signInButton").queryAs(Button.class);

        robot.interact(signIn::requestFocus);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> hasVisibleRing(signIn));
        assertTrue(hasVisibleRing(signIn), "the focused button must paint a 2 px ring in the focus token");

        // Focus moves: the ring must follow it instead of staying behind on the previous control.
        Node usernameNode = robot.lookup("#usernameField").query();
        robot.interact(usernameNode::requestFocus);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> !hasVisibleRing(signIn));
        assertFalse(hasVisibleRing(signIn), "the ring must leave the button that lost the focus");

        TextField username = robot.lookup("#usernameField").queryAs(TextField.class);
        robot.interact(username::requestFocus);
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                () -> username.getBorder() != null && strokeWidth(username) >= 2);
        assertTrue(strokeWidth(username) >= 2, "a focused field must thicken its border to 2 px");
    }

    @Test
    @DisplayName("Arabic switches on the Arabic typography, not only the direction")
    void arabicTurnsOnTheArabicTypography(FxRobot robot) throws Exception {
        signIn(robot);
        Label frenchLabel = firstNavLabel(robot);
        double frenchSize = frenchLabel.getFont().getSize();

        robot.interact(() -> I18n.get().setLocale(Locale.forLanguageTag("ar")));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> stage.getScene().getRoot().getStyleClass().contains(I18n.ARABIC_STYLE_CLASS));
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals(NodeOrientation.RIGHT_TO_LEFT, stage.getScene().getRoot().getNodeOrientation(),
                "Arabic must still mirror the layout");
        Label arabicLabel = firstNavLabel(robot);
        assertTrue(arabicLabel.getFont().getSize() > frenchSize,
                "Arabic must run larger than Latin: " + arabicLabel.getFont().getSize() + " vs " + frenchSize);

        robot.interact(() -> I18n.get().setLocale(Locale.FRENCH));
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS,
                () -> !stage.getScene().getRoot().getStyleClass().contains(I18n.ARABIC_STYLE_CLASS));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(frenchSize, firstNavLabel(robot).getFont().getSize(), 0.01,
                "returning to French must restore the Latin type size");
    }

    @Test
    @DisplayName("icon-only header controls carry an accessible name")
    void iconOnlyControlsAreNamed(FxRobot robot) throws Exception {
        signIn(robot);
        for (String id : new String[] {"#menuButton", "#notificationButton", "#themeButton", "#languageButton"}) {
            Node node = robot.lookup(id).query();
            assertNotNull(node, "the header must contain " + id);
            String name = node.getAccessibleText();
            assertTrue(name != null && !name.isBlank(),
                    id + " is icon-only and must expose an accessible name, found: " + name);
        }
    }

    // ------------------------------------------------------------------ helpers

    private void signIn(FxRobot robot) throws Exception {
        TextField username = robot.lookup("#usernameField").queryAs(TextField.class);
        TextField password = robot.lookup("#passwordField").queryAs(TextField.class);

        robot.clickOn(username).push(KeyCode.CONTROL, KeyCode.A).write(USERNAME);
        robot.clickOn(password).push(KeyCode.CONTROL, KeyCode.A).write(PASSWORD);
        robot.clickOn("#signInButton");

        WaitForAsyncUtils.waitFor(30, TimeUnit.SECONDS, () -> AppContext.get().navigation() != null
                && AppContext.get().navigation().current() != null);
        WaitForAsyncUtils.waitFor(15, TimeUnit.SECONDS, () -> !robot.lookup(".nav-item").queryAll().isEmpty());
        WaitForAsyncUtils.waitForFxEvents();
    }

    private Label firstNavLabel(FxRobot robot) {
        return robot.lookup(".nav-label").queryAll().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no navigation label found"));
    }

    /** True when the node paints a border stroke of at least 2 px in a colour that is not transparent. */
    private static boolean hasVisibleRing(Node node) {
        if (!(node instanceof Region region)) {
            return false;
        }
        Border border = region.getBorder();
        if (border == null || border.getStrokes().isEmpty()) {
            return false;
        }
        BorderStroke stroke = border.getStrokes().get(0);
        return stroke.getWidths().getTop() >= 2 && isVisible(stroke.getTopStroke());
    }

    private static double strokeWidth(Node node) {
        if (!(node instanceof Region region) || region.getBorder() == null
                || region.getBorder().getStrokes().isEmpty()) {
            return 0;
        }
        return region.getBorder().getStrokes().get(0).getWidths().getTop();
    }

    private static boolean isVisible(Paint paint) {
        return paint instanceof Color colour ? colour.getOpacity() > 0.5 : paint != null;
    }
}
