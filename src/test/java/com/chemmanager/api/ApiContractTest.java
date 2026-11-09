package com.chemmanager.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the seam between the desktop client and the PHP API.
 *
 * <p>The route table of the server ({@code backend/src/routes.php}) is parsed at test time: every
 * path declared in {@link ApiEndpoints} must exist there. A route renamed or removed on the server
 * therefore fails the build instead of failing in front of a user, which is exactly how the
 * Phase-1 assumptions silently drifted from the real API.</p>
 */
class ApiContractTest {

    private static final Path ROUTES = Path.of("backend", "src", "routes.php");

    private static final Pattern ROUTE = Pattern.compile(
            "\\$router->add\\('([A-Z]+)',\\s*'([^']+)'");

    @Test
    @DisplayName("every endpoint used by the client exists in backend/src/routes.php")
    void everyClientEndpointExistsOnTheServer() throws IOException, IllegalAccessException {
        assertTrue(Files.isRegularFile(ROUTES), "backend/src/routes.php must be part of the repository");
        String source = Files.readString(ROUTES, StandardCharsets.UTF_8);

        Set<String> serverPaths = new LinkedHashSet<>();
        Matcher matcher = ROUTE.matcher(source);
        while (matcher.find()) {
            serverPaths.add(matcher.group(2));
        }
        assertFalse(serverPaths.isEmpty(), "no route could be parsed from routes.php");

        List<String> missing = new ArrayList<>();
        for (String path : declaredPaths()) {
            if (!serverPaths.contains(path)) {
                missing.add(path);
            }
        }

        assertTrue(missing.isEmpty(),
                "these ApiEndpoints paths do not exist in backend/src/routes.php: " + missing
                        + " (server routes: " + serverPaths + ")");
    }

    @Test
    @DisplayName("the client paths use the /api prefix and camelCase identifiers")
    void pathsFollowTheServerConvention() throws IllegalAccessException {
        for (String path : declaredPaths()) {
            assertTrue(path.startsWith("/api/"), path + " must start with /api/");
            assertFalse(path.contains("-"), path + " must use camelCase, not kebab-case");
            assertFalse(path.endsWith("/"), path + " must not end with a slash");
        }
    }

    private static List<String> declaredPaths() throws IllegalAccessException {
        List<String> paths = new ArrayList<>();
        for (Field field : ApiEndpoints.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                paths.add((String) field.get(null));
            }
        }
        return paths;
    }
}
