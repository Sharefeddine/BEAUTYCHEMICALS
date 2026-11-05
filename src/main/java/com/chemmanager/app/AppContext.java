package com.chemmanager.app;

import java.util.ResourceBundle;

import com.chemmanager.config.ThemeManager;
import com.chemmanager.controller.MainWindowController;
import com.chemmanager.localization.I18n;
import com.chemmanager.navigation.NavigationManager;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.security.Session;
import com.chemmanager.security.SessionContext;
import com.chemmanager.service.ServiceFactory;
import com.chemmanager.util.AppIcon;
import com.chemmanager.util.FxTasks;

import javafx.fxml.FXMLLoader;
import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Application-level coordinator: owns the primary stage, builds the login and shell scenes,
 * applies the theme and the text direction (RTL for Arabic) and handles sign-out.
 */
public final class AppContext {

    private static final AppContext INSTANCE = new AppContext();

    private Stage stage;
    private NavigationManager navigation;
    private MainWindowController shellController;

    private AppContext() {
    }

    public static AppContext get() {
        return INSTANCE;
    }

    public void boot(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle(com.chemmanager.config.AppConfig.get().appName());
        stage.getIcons().add(AppIcon.image());
        stage.setMinWidth(1180);
        stage.setMinHeight(720);
        stage.setOnCloseRequest(event -> FxTasks.shutdown());
        showLogin();
    }

    public void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(AppContext.class.getResource("/fxml/login.fxml"));
            loader.setResources(ResourceBundle.getBundle("i18n.messages", I18n.get().locale()));
            Parent root = loader.load();
            Scene scene = buildScene(root, 1120, 700);
            stage.setScene(scene);
            stage.setWidth(1120);
            stage.setHeight(700);
            stage.centerOnScreen();
            stage.show();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to open the login screen", ex);
        }
    }

    public void showShell() {
        Session session = SessionContext.get().session();
        if (session == null) {
            showLogin();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(AppContext.class.getResource("/fxml/main-window.fxml"));
            loader.setResources(ResourceBundle.getBundle("i18n.messages", I18n.get().locale()));
            Parent root = loader.load();
            shellController = loader.getController();
            Scene scene = buildScene(root, 1440, 900);
            stage.setScene(scene);
            stage.setWidth(Math.max(1440, stage.getWidth()));
            stage.setHeight(Math.max(900, stage.getHeight()));
            stage.setMaximized(true);
            shellController.onShellReady();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to open the main window", ex);
        }
    }

    public void logout() {
        Session session = SessionContext.get().session();
        if (session != null) {
            try {
                ServiceFactory.auth().logout(session);
            } catch (RuntimeException ignored) {
                // signing out locally must always succeed
            }
        }
        SessionContext.get().clear();
        navigation = null;
        shellController = null;
        showLogin();
    }

    public void applyLocaleDirection(Parent root) {
        root.setNodeOrientation(I18n.get().isRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
    }

    public void navigate(NavigationModule module) {
        if (navigation != null) {
            navigation.show(module);
        }
    }

    public void setNavigation(NavigationManager manager) {
        this.navigation = manager;
    }

    public NavigationManager navigation() {
        return navigation;
    }

    public Stage stage() {
        return stage;
    }

    public MainWindowController shell() {
        return shellController;
    }

    private Scene buildScene(Parent root, double width, double height) {
        applyLocaleDirection(root);
        Scene scene = new Scene(root, width, height);
        ThemeManager.bind(scene);
        return scene;
    }
}
