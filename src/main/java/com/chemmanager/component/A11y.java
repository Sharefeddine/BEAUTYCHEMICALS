package com.chemmanager.component;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;

/**
 * Screen-reader and keyboard affordances in one place (design system section 7).
 *
 * <p>An icon-only control carries no text of its own, so the visible tooltip must also become the
 * accessible name: assistive technology reads {@code accessibleText}, not the tooltip bubble.
 * Charts get a one-line summary of what they plot, which is the only way a non-visual reader can
 * learn that a chart exists at all.</p>
 */
public final class A11y {

    private A11y() {
    }

    /** Names a control for assistive technology and installs the same text as its tooltip. */
    public static <T extends Node> T describe(T node, String text) {
        if (node == null || text == null || text.isBlank()) {
            return node;
        }
        node.setAccessibleText(text);
        Tooltip.install(node, new Tooltip(text));
        return node;
    }

    /** Names a control that already installs its own tooltip, without touching that tooltip. */
    public static void name(Node node, String text) {
        if (node != null && text != null && !text.isBlank()) {
            node.setAccessibleText(text);
        }
    }
}
