package com.chemmanager.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.paint.Color;

/**
 * Reads the colours of a theme stylesheet so Java code that draws outside the scene graph — the
 * window background, the application icon — uses the same tokens as the interface.
 *
 * <p>Hex literals used to be copied into Java, where they stopped following the theme and escaped
 * the contrast gate. {@code tools/ui/checkDesign.py} now refuses a colour literal in
 * {@code src/main/java}, so this class is the only way to reach a colour from code.</p>
 */
public final class ThemePalette {

    private static final Pattern TOKEN = Pattern.compile("^\\s*(-c-[a-z0-9-]+)\\s*:\\s*([^;]+);", Pattern.MULTILINE);
    private static final Pattern RGBA = Pattern.compile(
            "rgba?\\(\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\)");
    private static final Map<Theme, Map<String, String>> CACHE = new EnumMap<>(Theme.class);

    private ThemePalette() {
    }

    /** Colour of a token in the active theme. */
    public static Color color(String token) {
        return color(ThemeManager.current(), token);
    }

    /** Colour of a token in a given theme. */
    public static Color color(Theme theme, String token) {
        String value = tokens(theme).get(token);
        if (value == null) {
            throw new IllegalArgumentException("No theme token named " + token + " in " + theme.stylesheet());
        }
        return parse(token, value.trim());
    }

    private static Color parse(String token, String value) {
        if (value.startsWith("#")) {
            return Color.web(value);
        }
        Matcher rgba = RGBA.matcher(value);
        if (rgba.matches()) {
            double alpha = rgba.group(4) == null ? 1 : Double.parseDouble(rgba.group(4));
            return Color.color(Double.parseDouble(rgba.group(1)) / 255d,
                    Double.parseDouble(rgba.group(2)) / 255d,
                    Double.parseDouble(rgba.group(3)) / 255d, alpha);
        }
        throw new IllegalArgumentException("Token " + token + " is not a colour: " + value);
    }

    private static Map<String, String> tokens(Theme theme) {
        return CACHE.computeIfAbsent(theme, ThemePalette::read);
    }

    private static Map<String, String> read(Theme theme) {
        String path = theme.stylesheet();
        try (InputStream stream = ThemePalette.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing stylesheet " + path);
            }
            String css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int root = css.indexOf(".root {");
            String body = root < 0 ? css : css.substring(root);
            Map<String, String> tokens = new LinkedHashMap<>();
            Matcher matcher = TOKEN.matcher(body);
            while (matcher.find()) {
                tokens.put(matcher.group(1), matcher.group(2));
            }
            return tokens;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + path, ex);
        }
    }
}
