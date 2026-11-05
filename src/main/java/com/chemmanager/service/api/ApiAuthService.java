package com.chemmanager.service.api;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.LoginRequest;
import com.chemmanager.dto.LoginResponse;
import com.chemmanager.localization.I18n;
import com.chemmanager.security.AuthException;
import com.chemmanager.security.Session;
import com.chemmanager.service.AuthService;

/** Authentication against the PHP API (bcrypt hashes verified server side). */
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
        LoginResponse response;
        try {
            response = client.post(ApiEndpoints.LOGIN,
                    new LoginRequest(username.trim(), password, I18n.get().locale().toLanguageTag()),
                    LoginResponse.class);
        } catch (com.chemmanager.api.ApiException ex) {
            if (ex.status() == 401) {
                throw new AuthException("login.error.invalid", "Invalid username or password.");
            }
            if (ex.status() == 423) {
                throw new AuthException("login.error.locked", "Account temporarily locked.");
            }
            throw new AuthException("login.error.server", ex.getMessage());
        }
        if (response == null || response.token() == null) {
            throw new AuthException("login.error.server", "The server returned an empty session.");
        }
        Instant expiresAt = Instant.now().plus(Duration.ofHours(12));
        if (response.expiresAt() != null && !response.expiresAt().isBlank()) {
            try {
                expiresAt = Instant.parse(response.expiresAt());
            } catch (DateTimeParseException ignored) {
                // keep the fallback TTL
            }
        }
        return new Session(response.token(), response.user(), Instant.now(), expiresAt, dataSourceLabel());
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
}
