package com.chemmanager.util;

import com.chemmanager.config.ThemePalette;

import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Generates the application icon at runtime (gradient tile + monogram), so the project does not
 * need to ship binary assets for the window/taskbar icon.
 */
public final class AppIcon {

    private AppIcon() {
    }

    public static Image image() {
        StackPane tile = new StackPane();
        Rectangle background = new Rectangle(64, 64);
        background.setArcWidth(18);
        background.setArcHeight(18);
        background.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, ThemePalette.color("-c-fill-brand-from")),
                new Stop(1, ThemePalette.color("-c-fill-brand-to"))));
        Text monogram = new Text("C");
        monogram.setFont(Font.font("SansSerif", FontWeight.BOLD, 34));
        monogram.setFill(ThemePalette.color("-c-on-fill"));
        tile.getChildren().addAll(background, monogram);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage image = new WritableImage(64, 64);
        return tile.snapshot(parameters, image);
    }
}
