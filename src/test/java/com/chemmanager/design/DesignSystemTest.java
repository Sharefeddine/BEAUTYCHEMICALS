package com.chemmanager.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.chemmanager.config.Theme;
import com.chemmanager.config.ThemePalette;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the design system described in {@code docs/03-design-system.md}.
 *
 * <p>The rules used to live in prose only, which is how a stylesheet drifts: a colour stops
 * meeting 4.5:1, a 9 px label appears, a token is used but never declared. Each test below fails
 * when one of those things happens, in both themes. {@code tools/ui/checkDesign.py} enforces the
 * same contract before a push; this test is the gate the desktop job runs.</p>
 */
class DesignSystemTest {

    private static final Path DOC = Path.of("docs", "03-design-system.md");
    private static final List<Double> TYPE_SCALE =
            List.of(11.0, 11.5, 12.5, 13.5, 15.0, 17.0, 20.0, 22.0, 27.0);

    private static final Pattern TOKEN = Pattern.compile("^\\s*(-c-[a-z0-9-]+)\\s*:\\s*([^;]+);", Pattern.MULTILINE);
    private static final Pattern TOKEN_USE = Pattern.compile("-c-[a-z0-9-]+");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern COLOUR_FUNCTION = Pattern.compile("\\b(rgba?|hsla?)\\s*\\(");
    private static final Pattern NAMED_COLOUR = Pattern.compile(
            "(?<![\\w-])(white|black|red|green|blue|yellow|orange|grey|gray|silver|maroon|navy|teal|purple"
                    + "|olive|lime|aqua|fuchsia)(?![\\w-])");
    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern FONT_SIZE = Pattern.compile("-fx-font-size\\s*:\\s*([0-9.]+)\\s*(px|em)\\s*;");
    private static final Pattern DOC_ROW = Pattern.compile(
            "^\\|\\s*`(-c-[a-z0-9-]+)`\\s*\\|\\s*`([^`]+)`\\s*\\|\\s*`([^`]+)`\\s*\\|", Pattern.MULTILINE);

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("both themes declare exactly the same tokens")
    void themesDeclareTheSameTokens() {
        Map<String, String> light = tokens("/css/theme-light.css");
        Map<String, String> dark = tokens("/css/theme-dark.css");

        assertTrue(light.size() >= 100, "the token set shrank unexpectedly: " + light.size());
        Set<String> missingInDark = new LinkedHashSet<>(light.keySet());
        missingInDark.removeAll(dark.keySet());
        Set<String> missingInLight = new LinkedHashSet<>(dark.keySet());
        missingInLight.removeAll(light.keySet());

        assertTrue(missingInDark.isEmpty(), "theme-dark.css is missing " + missingInDark);
        assertTrue(missingInLight.isEmpty(), "theme-light.css is missing " + missingInLight);
    }

    @Test
    @DisplayName("every contrast pair meets its WCAG threshold in both themes")
    void contrastContractHolds() {
        Map<String, String> light = tokens("/css/theme-light.css");
        Map<String, String> dark = tokens("/css/theme-dark.css");
        List<String> problems = new ArrayList<>();

        for (String line : resource("/css/contrast-pairs.txt").split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            assertEquals(3, parts.length, "malformed contrast pair: " + trimmed);
            String foreground = parts[0];
            String background = parts[1];
            double minimum = Double.parseDouble(parts[2]);
            for (Map.Entry<String, Map<String, String>> theme : Map.of("light", light, "dark", dark).entrySet()) {
                String fg = theme.getValue().get(foreground);
                String bg = theme.getValue().get(background);
                if (fg == null || bg == null) {
                    problems.add(theme.getKey() + ": undeclared token in " + trimmed);
                } else if (!fg.startsWith("#") || !bg.startsWith("#")) {
                    problems.add(theme.getKey() + ": pair " + foreground + " on " + background
                            + " must use solid hex tokens (" + fg + " / " + bg + ")");
                } else {
                    double ratio = contrast(fg, bg);
                    if (ratio + 1e-9 < minimum) {
                        problems.add(String.format("%s: %s on %s = %.2f < %.2f",
                                theme.getKey(), foreground, background, ratio, minimum));
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "contrast regressions:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("app.css contains no colour literal and no undeclared token")
    void stylesheetOnlyUsesTokens() {
        String css = withoutComments(resource("/css/app.css"));
        Map<String, String> light = tokens("/css/theme-light.css");
        List<String> problems = new ArrayList<>();

        Matcher hex = HEX.matcher(css);
        while (hex.find()) {
            problems.add("hardcoded colour " + hex.group());
        }
        Matcher function = COLOUR_FUNCTION.matcher(css);
        while (function.find()) {
            problems.add("hardcoded colour function " + function.group(1) + "(...)");
        }
        Matcher named = NAMED_COLOUR.matcher(css);
        while (named.find()) {
            problems.add("hardcoded colour keyword " + named.group(1));
        }
        for (String used : new LinkedHashSet<>(all(TOKEN_USE, css))) {
            if (!light.containsKey(used)) {
                problems.add("uses " + used + ", which no theme declares");
            }
        }
        assertTrue(problems.isEmpty(), "app.css escapes the token system:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("no font size is below 11 px and every size belongs to the scale")
    void typographyFollowsTheScale() {
        String css = withoutComments(resource("/css/app.css"));
        List<String> problems = new ArrayList<>();

        Matcher rule = RULE.matcher(css);
        while (rule.find()) {
            String selector = rule.group(1).trim();
            boolean arabic = selector.contains(".lang-ar");
            Matcher size = FONT_SIZE.matcher(rule.group(2));
            while (size.find()) {
                double value = Double.parseDouble(size.group(1));
                if ("em".equals(size.group(2))) {
                    if (!arabic) {
                        problems.add(selector + ": em size outside the Arabic block");
                    }
                    continue;
                }
                if (value < 11) {
                    problems.add(selector + ": " + value + "px is below the 11 px floor");
                } else if (!TYPE_SCALE.contains(value)) {
                    problems.add(selector + ": " + value + "px is outside the type scale " + TYPE_SCALE);
                }
            }
        }
        assertTrue(problems.isEmpty(), "typography drifted:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("the document's token table matches the real theme files")
    void documentationMatchesTheThemes() throws IOException {
        assertTrue(Files.exists(DOC), "docs/03-design-system.md is missing");
        String doc = Files.readString(DOC, StandardCharsets.UTF_8);
        Map<String, String> light = tokens("/css/theme-light.css");
        Map<String, String> dark = tokens("/css/theme-dark.css");
        List<String> problems = new ArrayList<>();
        int rows = 0;

        Matcher row = DOC_ROW.matcher(doc);
        while (row.find()) {
            rows++;
            String token = row.group(1);
            if (!light.containsKey(token) || !dark.containsKey(token)) {
                problems.add("doc lists " + token + ", the themes do not declare it");
            } else if (!light.get(token).equals(row.group(2).trim()) || !dark.get(token).equals(row.group(3).trim())) {
                problems.add("doc says " + token + " is " + row.group(2) + "/" + row.group(3)
                        + ", the themes say " + light.get(token) + "/" + dark.get(token));
            }
        }
        assertTrue(rows >= 20, "the token table disappeared from the design document");
        assertTrue(problems.isEmpty(), "documentation drift:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("code and FXML carry no colour, no inline font size and no inline style")
    void codeCarriesNoColourOfItsOwn() {
        List<String> problems = new ArrayList<>();
        Pattern inlineStyle = Pattern.compile("\\sstyle\\s*=\\s*\"");
        Pattern setStyle = Pattern.compile("\\.setStyle\\s*\\(");

        problems.addAll(scan(Path.of("src", "main", "java"), ".java", HEX, "hardcodes a colour"));
        problems.addAll(scan(Path.of("src", "main", "java"), ".java", FONT_SIZE, "sets a font size inline"));
        problems.addAll(scan(Path.of("src", "main", "resources", "fxml"), ".fxml", HEX, "hardcodes a colour"));
        problems.addAll(scan(Path.of("src", "main", "resources", "fxml"), ".fxml", FONT_SIZE, "sets a font size inline"));
        problems.addAll(scan(Path.of("src", "main", "resources", "fxml"), ".fxml", inlineStyle, "carries an inline style"));
        problems.addAll(scan(Path.of("src", "main", "java"), ".java", setStyle, "calls setStyle"));

        // The single tolerated setStyle is the per-column alignment of DataTable, which JavaFX
        // cannot express with a style class; it must stay the only one.
        List<String> allowed = problems.stream().filter(p -> p.startsWith("DataTable.java")).toList();
        List<String> offenders = problems.stream().filter(p -> !p.startsWith("DataTable.java")).toList();

        assertTrue(offenders.isEmpty(), "code must take colours and sizes from the stylesheets: " + offenders);
        assertTrue(allowed.size() <= 1, "DataTable keeps at most one setStyle, found " + allowed);
    }

    @Test
    @DisplayName("ThemePalette resolves the tokens of both theme files")
    void themePaletteReadsTheThemeTokens() {
        for (Theme theme : Theme.values()) {
            for (String token : List.of("-c-bg", "-c-surface", "-c-text", "-c-on-fill",
                    "-c-fill-brand-from", "-c-fill-brand-to")) {
                assertNotNull(ThemePalette.color(theme, token),
                        token + " must resolve in " + theme.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    /** Every file below {@code folder} matching {@code pattern}, reported with its path. */
    private static List<String> scan(Path folder, String suffix, Pattern pattern, String what) {
        List<String> problems = new ArrayList<>();
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(suffix)).sorted().toList()) {
                String text = Files.readString(path, StandardCharsets.UTF_8);
                Matcher matcher = pattern.matcher(text);
                while (matcher.find()) {
                    problems.add(path.getFileName() + " " + what + ": " + matcher.group().trim());
                }
            }
        } catch (IOException ex) {
            fail("cannot scan " + folder + ": " + ex.getMessage());
        }
        return problems;
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, String> tokens(String resource) {
        String css = resource(resource);
        int root = css.indexOf(".root {");
        assertTrue(root >= 0, resource + " has no .root block");
        Map<String, String> tokens = new LinkedHashMap<>();
        Matcher matcher = TOKEN.matcher(css.substring(root));
        while (matcher.find()) {
            tokens.put(matcher.group(1), matcher.group(2).trim());
        }
        return tokens;
    }

    private static String resource(String path) {
        try (InputStream stream = DesignSystemTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + path, exception);
        }
    }

    private static String withoutComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static List<String> all(Pattern pattern, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        return found;
    }

    /** WCAG 2.2 relative-luminance contrast ratio for two {@code #rrggbb} colours. */
    private static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        if (value.length() == 3) {
            StringBuilder expanded = new StringBuilder();
            for (char c : value.toCharArray()) {
                expanded.append(c).append(c);
            }
            value = expanded.toString();
        }
        if (value.length() != 6) {
            fail("not a simple #rrggbb colour: " + hex);
        }
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double channel = Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16) / 255.0;
            channels[i] = channel <= 0.03928 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    }
}
