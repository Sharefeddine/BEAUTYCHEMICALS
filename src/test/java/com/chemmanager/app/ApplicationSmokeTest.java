package com.chemmanager.app;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.chemmanager.localization.I18n;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.mock.MockAuthService;
import com.chemmanager.util.FxTasks;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * Boots the real application (login screen, shell, dashboard) and opens every module, so that
 * FXML loading errors, missing controller fields and CSS problems fail the build instead of
 * appearing on the customer's screen. Runs headless under Xvfb on CI; skipped when no display
 * is available (the pure unit tests still cover the domain layer).
 */
class ApplicationSmokeTest {

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

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                AppContext.get().boot(stage);
                assertNotNull(stage.getScene(), "the login scene must be installed");
                assertTrue(stage.getScene().getRoot().getStyleClass().contains("root"),
                        "the design system must be applied to the login scene");

                SessionContext.get().open(new MockAuthService().login("admin", "admin123"));
                AppContext.get().showShell();
                assertNotNull(AppContext.get().navigation(), "the shell must install the navigation");

                for (NavigationModule module : NavigationModule.values()) {
                    AppContext.get().navigate(module);
                }

                I18n.get().setLocale(Locale.forLanguageTag("ar"));
                I18n.get().setLocale(Locale.forLanguageTag("en"));
                I18n.get().setLocale(Locale.forLanguageTag("fr"));

                AppContext.get().logout();
                stage.close();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(90, TimeUnit.SECONDS), "the application must boot within 90 seconds");
        FxTasks.shutdown();
        if (failure.get() != null) {
            throw new AssertionError("application boot failed: " + failure.get(), failure.get());
        }
    }
}
