package com.chemmanager.component;

import com.chemmanager.localization.I18n;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Small helpers that keep forms visually consistent (label + input + hint). */
public final class FormFields {

    private FormFields() {
    }

    /** Label and hint given as i18n keys. */
    public static VBox field(String labelKey, Node input) {
        return fieldKey(labelKey, input, null);
    }

    /** Label and hint given as i18n keys. */
    public static VBox fieldKey(String labelKey, Node input, String hintKey) {
        return field(I18n.get().t(labelKey), input, hintKey == null ? null : I18n.get().t(hintKey));
    }

    /** Label and hint already translated (used when the text is data, not a key). */
    public static VBox field(String label, Node input, String hint) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("field-label");
        VBox box = new VBox(6, labelNode, input);
        if (hint != null && !hint.isBlank()) {
            Label hintNode = new Label(hint);
            hintNode.getStyleClass().add("field-hint");
            hintNode.setWrapText(true);
            box.getChildren().add(hintNode);
        }
        if (input instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    public static HBox row(Node... nodes) {
        HBox row = new HBox(14, nodes);
        row.setAlignment(Pos.TOP_LEFT);
        for (Node node : nodes) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        return row;
    }

    public static Label sectionTitle(String key) {
        Label label = new Label(I18n.get().t(key));
        label.getStyleClass().add("h3");
        return label;
    }

    public static Label description(String key) {
        Label label = new Label(I18n.get().t(key));
        label.getStyleClass().add("text-muted");
        label.setWrapText(true);
        return label;
    }
}
