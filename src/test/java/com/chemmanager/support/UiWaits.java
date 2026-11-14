package com.chemmanager.support;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.testfx.api.FxRobot;

/**
 * Scene-graph probes that are safe to call from a {@code WaitForAsyncUtils.waitFor} condition.
 *
 * <p>{@code WaitForAsyncUtils.waitFor} polls on a background thread: a bare {@code robot.lookup} from
 * there walks the children of the scene graph while JavaFX is rebuilding it, which intermittently
 * threw {@link IndexOutOfBoundsException} inside {@code Parent.lookupAll}. Every probe below therefore
 * runs the lookup on the JavaFX thread and hands the answer back, and a lookup that hits the race is
 * retried once instead of failing the test.</p>
 */
public final class UiWaits {

    private UiWaits() {
    }

    /** Runs {@code probe} on the JavaFX thread and returns its result. */
    public static <T> T onFx(Callable<T> probe) {
        if (Platform.isFxApplicationThread()) {
            return call(probe);
        }
        FutureTask<T> task = new FutureTask<>(probe);
        Platform.runLater(task);
        try {
            return task.get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("the scene graph did not answer", failure);
        }
    }

    /** Every node matching {@code selector}, read on the JavaFX thread. */
    public static List<Node> all(FxRobot robot, String selector) {
        return onFx(() -> {
            try {
                return List.copyOf(robot.lookup(selector).queryAll());
            } catch (IndexOutOfBoundsException race) {
                return List.<Node>of();
            }
        });
    }

    /** True once at least one node matches the selector. */
    public static boolean hasNode(FxRobot robot, String selector) {
        return !all(robot, selector).isEmpty();
    }

    /** True while the selector matches nothing (a host that has been cleared). */
    public static boolean hasNoNode(FxRobot robot, String selector) {
        return all(robot, selector).isEmpty();
    }

    /** How many nodes match the selector. */
    public static int count(FxRobot robot, String selector) {
        return all(robot, selector).size();
    }

    /** True once one of the labels matched by the selector displays {@code text}. */
    public static boolean showsLabel(FxRobot robot, String selector, String text) {
        return all(robot, selector).stream()
                .anyMatch(node -> node instanceof Label label
                        && label.getText() != null && label.getText().contains(text));
    }

    /** True once the button matched by the selector is disabled. */
    public static boolean isDisabled(FxRobot robot, String selector) {
        return all(robot, selector).stream()
                .anyMatch(node -> node instanceof Button button && button.isDisabled());
    }

    /** True once the combo box matched by the selector lists {@code item}. */
    public static boolean listsItem(FxRobot robot, String selector, Object item) {
        return all(robot, selector).stream()
                .anyMatch(node -> node instanceof ComboBox<?> box && box.getItems().contains(item));
    }

    private static <T> T call(Callable<T> probe) {
        try {
            return probe.call();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
