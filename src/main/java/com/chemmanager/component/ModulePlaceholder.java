package com.chemmanager.component;

import java.util.function.Consumer;

import com.chemmanager.localization.I18n;
import com.chemmanager.navigation.NavigationModule;
import com.chemmanager.util.Labels;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Screen shown by modules whose backend is scheduled for a later phase.
 * It is explicit about the state (never a fake button): the module title, its delivery phase,
 * the planned capabilities and a working shortcut back to the dashboard.
 */
public final class ModulePlaceholder {

    private ModulePlaceholder() {
    }

    public static Node create(NavigationModule module, Consumer<NavigationModule> navigator) {
        I18n i18n = I18n.get();
        String base = "module." + module.id().replace('-', '_');

        StackPane iconBox = new StackPane(Icons.glyph(module.icon(), 24));
        iconBox.getStyleClass().add("placeholder-icon");

        Label title = new Label(Labels.nav(module.id()));
        title.getStyleClass().add("placeholder-title");

        Label phaseBadge = StatusBadge.primary(i18n.t("phase.expected", module.phase()));

        HBox titleRow = new HBox(12, title, phaseBadge);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(i18n.t(base + ".description"));
        description.getStyleClass().add("placeholder-text");
        description.setWrapText(true);

        Label capabilitiesTitle = new Label(i18n.t("module.planned_capabilities"));
        capabilitiesTitle.getStyleClass().add("h3");

        VBox features = new VBox(8);
        String rawFeatures = i18n.t(base + ".features");
        if (!rawFeatures.startsWith("!")) {
            for (String feature : rawFeatures.split("\\|")) {
                if (feature.isBlank()) {
                    continue;
                }
                Label bullet = new Label(feature.trim());
                HBox item = new HBox(9, Icons.glyph(Icons.Icon.CHECK, 11), bullet);
                item.getStyleClass().add("feature-item");
                item.setAlignment(Pos.CENTER_LEFT);
                features.getChildren().add(item);
            }
        }

        Label notice = new Label(i18n.t("module.not_implemented_notice"));
        notice.getStyleClass().add("text-muted");
        notice.setStyle("-fx-font-size: 11.5px;");
        notice.setWrapText(true);

        Button dashboardButton = new Button(i18n.t("module.back_to_dashboard"));
        dashboardButton.getStyleClass().addAll("btn", "btn-primary");
        dashboardButton.setGraphic(Icons.glyph(Icons.Icon.DASHBOARD, 12));
        dashboardButton.setOnAction(event -> navigator.accept(NavigationModule.DASHBOARD));

        Button roadmapButton = new Button(i18n.t("module.view_roadmap"));
        roadmapButton.getStyleClass().addAll("btn", "btn-secondary");
        roadmapButton.setGraphic(Icons.glyph(Icons.Icon.LIST, 12));
        roadmapButton.setOnAction(event -> Dialogs.info(dashboardButton.getScene().getWindow(),
                i18n.t("module.roadmap_title"),
                i18n.t("module.roadmap_body", Labels.nav(module.id()), module.phase())));

        HBox actions = new HBox(10, dashboardButton, roadmapButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, iconBox, titleRow, description,
                new HBox(new VBox(10, capabilitiesTitle, features)), notice, actions);
        card.getStyleClass().add("placeholder-card");
        card.setAlignment(Pos.TOP_LEFT);

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.TOP_LEFT);
        return wrapper;
    }
}
