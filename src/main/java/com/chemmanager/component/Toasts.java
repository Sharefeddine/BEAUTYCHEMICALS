package com.chemmanager.component;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Lightweight toast notifications rendered on top of the shell (bottom-end corner).
 * Used for confirmations, exports and background task results.
 */
public final class Toasts {

    /** Toast variants. */
    public enum Kind {
        SUCCESS("toast-success", Icons.Icon.CHECK_CIRCLE),
        INFO("toast-info", Icons.Icon.INFO),
        WARNING("toast-warning", Icons.Icon.WARNING),
        ERROR("toast-danger", Icons.Icon.DANGER);

        private final String styleClass;
        private final Icons.Icon icon;

        Kind(String styleClass, Icons.Icon icon) {
            this.styleClass = styleClass;
            this.icon = icon;
        }
    }

    private static StackPane host;
    private static VBox container;

    private Toasts() {
    }

    public static void install(StackPane overlayHost) {
        host = overlayHost;
        container = new VBox(10);
        container.setAlignment(Pos.BOTTOM_RIGHT);
        container.setMouseTransparent(true);
        StackPane.setAlignment(container, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(container, new Insets(0, 24, 24, 0));
        host.getChildren().add(container);
    }

    public static void success(String message) {
        show(message, Kind.SUCCESS);
    }

    public static void info(String message) {
        show(message, Kind.INFO);
    }

    public static void warning(String message) {
        show(message, Kind.WARNING);
    }

    public static void error(String message) {
        show(message, Kind.ERROR);
    }

    public static void show(String message, Kind kind) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(message, kind));
            return;
        }
        if (host == null || container == null) {
            System.out.println("[" + kind + "] " + message);
            return;
        }
        Label text = new Label(message);
        text.getStyleClass().add("toast-message");
        text.setWrapText(true);
        HBox toast = new HBox(10, Icons.glyph(kind.icon, 15), text);
        toast.getStyleClass().addAll("toast", kind.styleClass);
        toast.setAlignment(Pos.CENTER_LEFT);

        container.getChildren().add(toast);
        if (container.getChildren().size() > 4) {
            container.getChildren().remove(0);
        }

        FadeTransition in = new FadeTransition(Duration.millis(180), toast);
        in.setFromValue(0);
        in.setToValue(1);
        PauseTransition wait = new PauseTransition(Duration.seconds(4));
        FadeTransition out = new FadeTransition(Duration.millis(320), toast);
        out.setFromValue(1);
        out.setToValue(0);
        SequentialTransition sequence = new SequentialTransition(in, wait, out);
        sequence.setOnFinished(event -> container.getChildren().remove(toast));
        sequence.play();
    }
}
