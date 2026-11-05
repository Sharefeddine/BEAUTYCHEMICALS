package com.chemmanager.component;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Reusable panel with a title, subtitle, action slot and body — the building block of every screen. */
public final class Card extends VBox {

    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final HBox actions = new HBox(8);
    private final VBox body = new VBox();

    public Card(String title, String subtitle) {
        super(0);
        getStyleClass().add("card");
        titleLabel.getStyleClass().add("card-title");
        subtitleLabel.getStyleClass().add("card-subtitle");
        subtitleLabel.setManaged(false);
        subtitleLabel.setVisible(false);

        VBox titles = new VBox(2, titleLabel, subtitleLabel);
        titles.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(10, titles, spacer, actions);
        header.getStyleClass().add("card-header");
        header.setAlignment(Pos.CENTER_LEFT);

        body.getStyleClass().add("card-body");
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(header, body);
        title(title);
    }

    public Card title(String text) {
        titleLabel.setText(text);
        return this;
    }

    public Card subtitle(String text) {
        subtitleLabel.setText(text);
        boolean visible = text != null && !text.isBlank();
        subtitleLabel.setVisible(visible);
        subtitleLabel.setManaged(visible);
        return this;
    }

    public Card action(Node node) {
        actions.getChildren().add(node);
        return this;
    }

    public Card body(Node... nodes) {
        body.getChildren().setAll(nodes);
        return this;
    }

    public Card flush() {
        body.getStyleClass().remove("card-body");
        body.getStyleClass().add("card-body-flush");
        return this;
    }

    public VBox bodyBox() {
        return body;
    }

    public Label titleLabelControl() {
        return titleLabel;
    }

    public Label subtitleLabelControl() {
        return subtitleLabel;
    }
}
