package com.chemmanager.navigation;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.function.Consumer;

import com.chemmanager.component.Icons;
import com.chemmanager.localization.I18n;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Central navigation: loads module FXML into the content host with a short fade transition,
 * keeps track of the active module and survives locale / theme changes by reloading the view.
 */
public final class NavigationManager {

    private final StackPane contentHost;
    private final Consumer<NavigationModule> onModuleChanged;
    private final ReadOnlyObjectWrapper<NavigationModule> current = new ReadOnlyObjectWrapper<>();

    public NavigationManager(StackPane contentHost, Consumer<NavigationModule> onModuleChanged) {
        this.contentHost = contentHost;
        this.onModuleChanged = onModuleChanged;
    }

    public ReadOnlyObjectProperty<NavigationModule> currentProperty() {
        return current.getReadOnlyProperty();
    }

    public NavigationModule current() {
        return current.get();
    }

    public void show(NavigationModule module) {
        if (module == null) {
            return;
        }
        Node view = load(module);
        contentHost.getChildren().setAll(view);
        view.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(180), view);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setOnFinished(event -> view.setOpacity(1));
        fade.play();
        // Safety net: while the window is being shown or resized the animation pulse can be
        // suspended, which would leave the screen invisible. Force the final state anyway.
        PauseTransition watchdog = new PauseTransition(Duration.millis(260));
        watchdog.setOnFinished(event -> view.setOpacity(1));
        watchdog.play();
        current.set(module);
        if (onModuleChanged != null) {
            onModuleChanged.accept(module);
        }
    }

    /** Reloads the current screen (used after a language or theme change). */
    public void refresh() {
        NavigationModule module = current.get();
        if (module != null) {
            show(module);
        }
    }

    private Node load(NavigationModule module) {
        try {
            FXMLLoader loader = new FXMLLoader(NavigationManager.class.getResource(module.fxmlPath()));
            loader.setResources(ResourceBundle.getBundle("i18n.messages", I18n.get().locale()));
            return loader.load();
        } catch (IOException | RuntimeException ex) {
            return errorView(module, ex);
        }
    }

    /** Never let a broken screen take the whole shell down. */
    private Node errorView(NavigationModule module, Throwable error) {
        Label title = new Label(I18n.get().t("error.view_title"));
        title.getStyleClass().add("h2");
        Label detail = new Label(I18n.get().t("error.view_message", module.id(), String.valueOf(error.getMessage())));
        detail.getStyleClass().add("text-muted");
        detail.setWrapText(true);
        VBox box = new VBox(10, Icons.glyph(Icons.Icon.DANGER, 22), title, detail);
        box.getStyleClass().add("card");
        box.setStyle("-fx-padding: 24;");
        error.printStackTrace();
        return box;
    }
}
