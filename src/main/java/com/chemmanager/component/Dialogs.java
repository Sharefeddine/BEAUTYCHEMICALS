package com.chemmanager.component;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import com.chemmanager.config.ThemeManager;
import com.chemmanager.localization.I18n;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Themed dialogs and file pickers used by every screen (no raw {@code Alert} elsewhere). */
public final class Dialogs {

    private Dialogs() {
    }

    public static void info(Window owner, String title, String message) {
        show(owner, Alert.AlertType.INFORMATION, title, message);
    }

    public static void warning(Window owner, String title, String message) {
        show(owner, Alert.AlertType.WARNING, title, message);
    }

    public static void error(Window owner, String title, String message) {
        show(owner, Alert.AlertType.ERROR, title, message);
    }

    public static boolean confirm(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        ButtonType cancel = new ButtonType(I18n.get().t("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType ok = new ButtonType(I18n.get().t("common.confirm"), ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(cancel, ok);
        style(alert.getDialogPane());
        return alert.showAndWait().filter(button -> button == ok).isPresent();
    }

    public static Optional<Path> chooseSaveFile(Window owner, String title, String suggestedName,
                                                String extensionDescription, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionDescription, "*." + extension));
        File file = chooser.showSaveDialog(owner);
        return file == null ? Optional.empty() : Optional.of(file.toPath());
    }

    public static Optional<Path> chooseOpenFile(Window owner, String title, String extensionDescription,
                                                String... extensions) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extensionDescription, extensions));
        File file = chooser.showOpenDialog(owner);
        return file == null ? Optional.empty() : Optional.of(file.toPath());
    }

    private static void show(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(new ButtonType(I18n.get().t("common.close"), ButtonBar.ButtonData.OK_DONE));
        style(alert.getDialogPane());
        alert.showAndWait();
    }

    /**
     * Themes a dialog pane and applies the active language to it.
     *
     * <p>A {@code Dialog} builds its own window, so it inherits neither the stylesheets of the main
     * scene nor its typography: without this call the editor, comparison, stock and purchasing
     * dialogs render in the default JavaFX look and stay left-to-right in Arabic.</p>
     */
    public static void applyTheme(DialogPane pane) {
        pane.getStylesheets().setAll(ThemeManager.activeStylesheets());
        if (!pane.getStyleClass().contains("dialog-pane")) {
            pane.getStyleClass().add("dialog-pane");
        }
        I18n.get().applyTo(pane);
    }

    private static void style(DialogPane pane) {
        applyTheme(pane);
        pane.getStyleClass().add("chemmanager-dialog");
        if (pane.getScene() != null && pane.getScene().getWindow() != null) {
            pane.getScene().getWindow().setUserData("themed");
        }
    }
}
