package com.chemmanager.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemeManager;
import com.chemmanager.localization.I18n;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.mock.MockAuthService;
import com.chemmanager.util.FxTasks;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Boots the real application (login screen, shell, every module) in a headless JavaFX session,
 * so that FXML loading errors, missing controller fields and CSS mistakes fail the build
 * instead of appearing on the customer's screen.
 *
 * <p>The run also captures screenshots of the main screens into {@code target/screenshots},
 * published by CI as visual evidence of the delivered interface. Runs under Xvfb on Linux;
 * the test is skipped when no display is available (the unit tests still cover the domain).</p>
 */
class ApplicationSmokeTest {

    private static final File SHOTS = new File("target/screenshots");
    private static boolean toolkitAvailable;

    @BeforeAll
    static void startToolkit() {
        try {
            Platform.startup(() -> { });
            Platform.setImplicitExit(false);
            toolkitAvailable = true;
        } catch (IllegalStateException alreadyRunning) {
            toolkitAvailable = true;
        } catch (RuntimeException | Error noDisplay) {
            toolkitAvailable = false;
        }
    }

    @Test
    void theShellBootsAndEveryModuleOpens() throws Exception {
        Assumptions.assumeTrue(toolkitAvailable, "JavaFX toolkit unavailable in this environment");
        SHOTS.mkdirs();

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        List<Runnable> steps = new ArrayList<>();
        Stage stage = newStage();

        steps.add(() -> {
            AppContext.get().boot(stage);
            assertNotNull(stage.getScene(), "the login scene must be installed");
            assertTrue(stage.getScene().getRoot().getStyleClass().contains("root"),
                    "the design system must be applied to the login scene");
        });
        steps.add(() -> shoot(stage, "01-login-light"));
        steps.add(() -> {
            SessionContext.get().open(new MockAuthService().login("admin", "admin123"));
            AppContext.get().showShell();
            assertNotNull(AppContext.get().navigation(), "the shell must install the navigation");
        });
        steps.add(() -> { });
        steps.add(this::assertContentVisible);
        steps.add(() -> shoot(stage, "02-dashboard-light"));
        addScreen(steps, stage, NavigationModule.PRODUCTS, "03-products");
        addScreen(steps, stage, NavigationModule.RAW_MATERIALS, "04-raw-materials");
        addScreen(steps, stage, NavigationModule.USERS, "05-users-permissions");
        addScreen(steps, stage, NavigationModule.SETTINGS, "06-settings");
        addScreen(steps, stage, NavigationModule.INVENTORY, "07-inventory");
        addScreen(steps, stage, NavigationModule.PURCHASING, "07b-purchasing");
        addScreen(steps, stage, NavigationModule.SUPPLIERS, "07c-suppliers");
        addScreen(steps, stage, NavigationModule.PRODUCTION, "07-planned-module");
        steps.add(() -> ThemeManager.apply(Theme.DARK));
        steps.add(() -> { });
        addScreen(steps, stage, NavigationModule.DASHBOARD, "08-dashboard-dark");
        steps.add(() -> I18n.get().setLocale(Locale.forLanguageTag("ar")));
        steps.add(() -> { });
        steps.add(() -> shoot(stage, "09-dashboard-arabic"));
        steps.add(() -> I18n.get().setLocale(Locale.forLanguageTag("en")));
        steps.add(() -> { });
        steps.add(() -> shoot(stage, "10-dashboard-english"));
        steps.add(() -> {
            I18n.get().setLocale(Locale.forLanguageTag("fr"));
            ThemeManager.apply(Theme.LIGHT);
        });
        steps.add(() -> {
            AppContext.get().logout();
            stage.close();
        });

        Timeline timeline = new Timeline();
        for (int index = 0; index < steps.size(); index++) {
            Runnable step = steps.get(index);
            boolean last = index == steps.size() - 1;
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(900L * (index + 1)), event -> {
                try {
                    step.run();
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    if (last) {
                        done.countDown();
                    }
                }
            }));
        }
        Platform.runLater(timeline::play);

        assertTrue(done.await(120, TimeUnit.SECONDS), "the application must boot within 120 seconds");
        FxTasks.shutdown();
        if (failure.get() != null) {
            throw new AssertionError("application boot failed: " + failure.get(), failure.get());
        }
        assertTrue(new File(SHOTS, "02-dashboard-light.png").length() > 0,
                "the dashboard screenshot must have been captured");
    }

    /** The first screen of the shell must be laid out and fully opaque, never blank. */
    private void assertContentVisible() {
        javafx.scene.Scene scene = AppContext.get().stage().getScene();
        // In a headless session the render pulse can be idle, so force the layout pass the way
        // a visible window would: the assertion must validate the screen, not the pulse.
        scene.getRoot().applyCss();
        scene.getRoot().layout();
        javafx.scene.Node host = scene.getRoot().lookup("#contentHost");
        assertNotNull(host, "the shell must expose its content host");
        javafx.scene.Parent pane = (javafx.scene.Parent) host;
        assertTrue(!pane.getChildrenUnmodifiable().isEmpty(), "the shell must open a module");
        // the last child of the StackPane is the visible screen
        javafx.scene.Node content = pane.getChildrenUnmodifiable()
                .get(pane.getChildrenUnmodifiable().size() - 1);
        assertEquals(1.0, content.getOpacity(), 0.001, "the first screen must be visible");
        assertTrue(content.getBoundsInLocal().getHeight() > 100,
                "the first screen must be laid out, height was " + content.getBoundsInLocal().getHeight());
    }

    /** Adds the two steps a screen needs: open it, then photograph it once the data is applied. */
    private static void addScreen(List<Runnable> steps, Stage stage, NavigationModule module, String shot) {
        steps.add(() -> AppContext.get().navigate(module));
        steps.add(() -> { });
        steps.add(() -> shoot(stage, shot));
    }

    private static Stage newStage() {
        AtomicReference<Stage> holder = new AtomicReference<>();
        try {
            CountDownLatch created = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    holder.set(new Stage());
                } finally {
                    created.countDown();
                }
            });
            created.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return holder.get();
    }

    /** Serialises the current scene to target/screenshots; failures never break the boot test. */
    private static void shoot(Stage stage, String name) {
        try {
            Scene scene = stage.getScene();
            if (scene == null) {
                return;
            }
            WritableImage image = scene.snapshot(null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", new File(SHOTS, name + ".png"));
        } catch (Exception ex) {
            System.err.println("screenshot " + name + " skipped: " + ex.getMessage());
        }
    }
}
