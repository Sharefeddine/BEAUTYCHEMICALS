package com.chemmanager.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Executive KPI tile: label, value, contextual hint, optional delta badge and coloured icon.
 * Used by the dashboard and reusable by any future analytics screen.
 */
public final class KpiCard extends HBox {

    private final Label titleLabel = new Label();
    private final Label valueLabel = new Label();
    private final Label hintLabel = new Label();
    private final Label trendLabel = new Label();

    public KpiCard(Icons.Icon icon, String accentStyleClass) {
        getStyleClass().add("kpi-card");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        titleLabel.getStyleClass().add("kpi-title");
        titleLabel.setWrapText(true);
        valueLabel.getStyleClass().add("kpi-value");
        hintLabel.getStyleClass().add("kpi-hint");
        trendLabel.getStyleClass().addAll("badge", "badge-success");
        trendLabel.setVisible(false);
        trendLabel.setManaged(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hintRow = new HBox(6, hintLabel, trendLabel);
        hintRow.setAlignment(Pos.CENTER_LEFT);

        VBox textBlock = new VBox(3, titleLabel, valueLabel, hintRow);
        textBlock.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(textBlock, Priority.ALWAYS);

        getChildren().addAll(textBlock, spacer, Icons.badge(icon, accentStyleClass, 16));
    }

    public KpiCard title(String text) {
        titleLabel.setText(text);
        return this;
    }

    public KpiCard value(String text) {
        valueLabel.setText(text);
        // A long figure (an amount in dinars, say) is shrunk rather than clipped: the tiles sit in
        // an equal-width row, so the whole number has to fit inside one of them.
        boolean longValue = text != null && text.length() > 12;
        valueLabel.getStyleClass().remove("kpi-value-compact");
        if (longValue) {
            valueLabel.getStyleClass().add("kpi-value-compact");
        }
        valueLabel.setTooltip(longValue ? new Tooltip(text) : null);
        return this;
    }

    public KpiCard hint(String text) {
        boolean visible = text != null && !text.isBlank();
        hintLabel.setText(visible ? text : "");
        hintLabel.setVisible(visible);
        hintLabel.setManaged(visible);
        return this;
    }

    public KpiCard trend(String text, boolean positive) {
        boolean visible = text != null && !text.isBlank();
        trendLabel.setText(visible ? text : "");
        trendLabel.getStyleClass().removeAll("badge-success", "badge-danger", "badge-neutral");
        trendLabel.getStyleClass().add(positive ? "badge-success" : "badge-danger");
        trendLabel.setVisible(visible);
        trendLabel.setManaged(visible);
        return this;
    }

    public KpiCard tooltip(String text) {
        if (text != null && !text.isBlank()) {
            Tooltip.install(this, new Tooltip(text));
        }
        return this;
    }
}
