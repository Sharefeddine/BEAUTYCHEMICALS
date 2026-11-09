package com.chemmanager.service.api;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.LoginRequest;
import com.chemmanager.localization.I18n;
import com.chemmanager.model.User;
import com.chemmanager.security.AuthException;
import com.chemmanager.security.Permission;
import com.chemmanager.security.Session;
import com.chemmanager.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Authentication against the PHP API.
 *
 * <p>The server verifies the bcrypt hash, issues an opaque bearer token and returns the effective
 * permission list of the account; the client never sees a hash and never trusts its own copy of
 * the matrix for authorisation decisions on the server side.</p>
 */
public final class ApiAuthService implements AuthService {

    private final ApiClient client;

    public ApiAuthService(ApiClient client) {
        this.client = client;
    }

    @Override
    public Session login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthException("login.error.required", "Username and password are required.");
        }
        JsonNode payload;
        try {
            payload = client.post(ApiEndpoints.LOGIN,
                    new LoginRequest(username.trim(), password, I18n.get().locale().toLanguageTag()),
                    JsonNode.class);
        } catch (com.chemmanager.api.ApiException ex) {
            if (ex.status() == 401) {
                throw new AuthException("login.error.invalid", "Invalid username or password.");
            }
            if (ex.status() == 429) {
                throw new AuthException("login.error.locked", "Account temporarily locked.");
            }
            if (ex.status() == 403 && "ACCOUNT_DISABLED".equals(ex.code())) {
                throw new AuthException("login.error.disabled", "This account is disabled.");
            }
            throw new AuthException("login.error.server", ex.getMessage());
        }
        if (payload == null || payload.path("token").asText("").isBlank()) {
            throw new AuthException("login.error.server", "The server returned an empty session.");
        }

        User user = ApiMapper.user(payload.path("user"));
        java.util.Set<Permission> permissions = ApiMapper.permissions(payload.path("permissions"));

        return new Session(payload.path("token").asText(), user, Instant.now(), expiry(payload),
                dataSourceLabel(), permissions);
    }

    @Override
    public void logout(Session session) {
        try {
            client.postVoid(ApiEndpoints.LOGOUT, null);
        } catch (RuntimeException ignored) {
            // the local session is cleared regardless of the server answer
        }
    }

    @Override
    public String dataSourceLabel() {
        return "PHP API";
    }

    /** Changes the password and revokes the other sessions (the server decides). */
    public void changePassword(String currentPassword, String newPassword) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("currentPassword", currentPassword);
        body.put("newPassword", newPassword);
        client.postVoid(ApiEndpoints.PASSWORD, body);
    }

    private static Instant expiry(JsonNode payload) {
        String raw = payload.path("expiresAt").asText("");
        if (!raw.isBlank()) {
            try {
                return Instant.parse(raw);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDateTime.parse(raw.replace(' ', 'T'))
                            .atZone(java.time.ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException ignoredAgain) {
                    // fall through to the default TTL
                }
            }
        }
        return Instant.now().plus(Duration.ofHours(8));
    }
}
