package com.chemmanager.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.chemmanager.model.User;

/** An authenticated user together with the API token and the effective permission set. */
public final class Session {

    private final String token;
    private final User user;
    private final Instant startedAt;
    private final Instant expiresAt;
    private final Set<Permission> permissions;
    private final String dataSourceLabel;

    public Session(String token, User user, Instant startedAt, Instant expiresAt, String dataSourceLabel) {
        this(token, user, startedAt, expiresAt, dataSourceLabel, permissionsOf(user));
    }

    /**
     * Session whose effective permissions come from the server (API mode: the API recomputes them
     * from rolePermissions on every request, so its answer is authoritative).
     */
    public Session(String token, User user, Instant startedAt, Instant expiresAt, String dataSourceLabel,
                   Set<Permission> permissions) {
        this.token = token;
        this.user = user;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.dataSourceLabel = dataSourceLabel;
        this.permissions = permissions == null || permissions.isEmpty()
                ? Set.copyOf(EnumSet.noneOf(Permission.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    private static Set<Permission> permissionsOf(User user) {
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        if (user != null && user.roles() != null) {
            user.roles().forEach(role -> effective.addAll(role.permissions()));
        }
        return effective;
    }

    public static Session of(User user, Duration ttl, String dataSourceLabel) {
        Instant now = Instant.now();
        return new Session("local-" + now.toEpochMilli(), user, now, now.plus(ttl), dataSourceLabel);
    }

    public String token() {
        return token;
    }

    public User user() {
        return user;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public String dataSourceLabel() {
        return dataSourceLabel;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    public boolean hasAny(Permission... candidates) {
        for (Permission candidate : candidates) {
            if (permissions.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public String primaryRoleCode() {
        List<Role> roles = user == null ? List.of() : user.roles();
        return roles.isEmpty() ? "VIEWER" : roles.get(0).code();
    }

    public Duration remaining() {
        Duration left = Duration.between(Instant.now(), expiresAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Throws when the session does not grant the permission — call before mutating actions. */
    public void require(Permission permission) {
        if (!has(permission)) {
            throw new AccessDeniedException(permission);
        }
        if (isExpired()) {
            throw new AccessDeniedException("Session expired — please sign in again.");
        }
    }
}
