package com.chemmanager.app;

import com.chemmanager.config.AppConfig;
import com.chemmanager.config.Theme;
import com.chemmanager.config.UserPreferences;
import com.chemmanager.localization.I18n;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.FxTasks;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * ChemManager ERP — JavaFX desktop entry point.
 *
 * <p>Start-up sequence: read configuration, resolve the service implementation
 * (demo dataset or PHP API), restore the user's theme/language, then show the login screen.</p>
 */
public class ChemManagerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        AppConfig config = AppConfig.get();
        ServiceFactory.initialise();
        I18n.get().setLocale(UserPreferences.get().locale());
        Theme theme = UserPreferences.get().theme();
        com.chemmanager.config.ThemeManager.apply(theme);
        System.out.printf("%s %s — data mode: %s (%s)%n", config.appName(), config.appVersion(),
                ServiceFactory.mode(), ServiceFactory.mode() == com.chemmanager.config.DataMode.API
                        ? ServiceFactory.apiClient().baseUrl() : "offline demo dataset");
        AppContext.get().boot(primaryStage);
    }

    @Override
    public void stop() {
        FxTasks.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
