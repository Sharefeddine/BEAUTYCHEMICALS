package com.chemmanager.service.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.chemmanager.repository.MockDataStore;
import com.chemmanager.security.AuthException;
import com.chemmanager.security.PasswordHasher;
import com.chemmanager.security.Session;
import com.chemmanager.service.AuthService;

/**
 * Offline authentication against the demo dataset. Mirrors the server-side rules:
 * hashed passwords, active-user check, throttling after repeated failures and
 * a session TTL that the shell honours.
 */
public final class MockAuthService implements AuthService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT = Duration.ofMinutes(2);
    private static final Duration SESSION_TTL = Duration.ofHours(8);

    private final MockDataStore store = MockDataStore.get();
    private final PasswordHasher hasher = new PasswordHasher();
    private final Map<String, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();

    @Override
    public Session login(String username, String password) {
        String normalized = username == null ? "" : username.trim().toLowerCase();
        if (normalized.isEmpty() || password == null || password.isEmpty()) {
            throw new AuthException("login.error.required", "Username and password are required.");
        }
        Instant lock = lockedUntil.get(normalized);
        if (lock != null && lock.isAfter(Instant.now())) {
            throw new AuthException("login.error.locked",
                    "Too many failed attempts. Try again in a couple of minutes.");
        }
        Optional<String> hash = store.passwordHash(normalized);
        boolean matches = hash.isPresent() && hasher.verify(password.toCharArray(), hash.get());
        if (hash.isEmpty() || !matches) {
            registerFailure(normalized);
            throw new AuthException("login.error.invalid", "Invalid username or password.");
        }
        var user = store.findUser(normalized)
                .orElseThrow(() -> new AuthException("login.error.invalid", "Invalid username or password."));
        if (!user.active()) {
            throw new AuthException("login.error.inactive", "This account is disabled.");
        }
        failures.remove(normalized);
        lockedUntil.remove(normalized);
        return Session.of(user, SESSION_TTL, dataSourceLabel());
    }

    @Override
    public void logout(Session session) {
        // Offline sessions are purely in-memory: nothing server-side to invalidate.
    }

    @Override
    public String dataSourceLabel() {
        return "Demo dataset";
    }

    private void registerFailure(String username) {
        int attempts = failures.computeIfAbsent(username, key -> new AtomicInteger()).incrementAndGet();
        if (attempts >= MAX_ATTEMPTS) {
            lockedUntil.put(username, Instant.now().plus(LOCKOUT));
            failures.remove(username);
        }
    }
}
