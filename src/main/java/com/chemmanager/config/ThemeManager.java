package com.chemmanager.config;

import java.util.Objects;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

/**
 * Applies theme stylesheets to a scene and keeps a single observable source of truth
 * for the currently selected {@link Theme}.
 */
public final class ThemeManager {

    private static final String BASE_STYLESHEET = "/css/app.css";

    private static Theme currentTheme = UserPreferences.get().theme();
    private static Scene boundScene;

    private ThemeManager() {
    }

    public static Theme current() {
        return currentTheme;
    }

    public static void bind(Scene scene) {
        boundScene = Objects.requireNonNull(scene, "scene");
        apply(currentTheme);
    }

    public static void apply(Theme theme) {
        currentTheme = theme;
        UserPreferences.get().setTheme(theme);
        if (boundScene != null) {
            String base = resource(BASE_STYLESHEET);
            String themeSheet = resource(theme.stylesheet());
            boundScene.getStylesheets().clear();
            boundScene.getStylesheets().addAll(base, themeSheet);
        }
    }

    public static void toggle() {
        apply(currentTheme.toggle());
    }

    /** Background colour of the splash/login window, taken from the active theme's tokens. */
    public static Color windowBackground() {
        return ThemePalette.color("-c-bg");
    }

    /** Stylesheets of the active theme, for dialogs and popovers created outside the scene graph. */
    public static java.util.List<String> activeStylesheets() {
        return java.util.List.of(resource(BASE_STYLESHEET), resource(currentTheme.stylesheet()));
    }

    private static String resource(String path) {
        var url = ThemeManager.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Missing stylesheet " + path);
        }
        return url.toExternalForm();
    }
}
