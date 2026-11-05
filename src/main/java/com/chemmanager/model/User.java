package com.chemmanager.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.chemmanager.security.Role;

/** An application user with the roles granted to it. */
public record User(long id, String username, String fullName, String email, String phone,
                   String jobTitle, List<Role> roles, boolean active, LocalDateTime lastLogin) {

    public User {
        Objects.requireNonNull(username, "username");
        roles = roles == null ? List.of() : List.copyOf(roles);
        fullName = fullName == null || fullName.isBlank() ? username : fullName;
    }

    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    public String roleCodes() {
        return roles.stream().map(Role::code).reduce((a, b) -> a + ", " + b).orElse("—");
    }

    public String primaryRoleCode() {
        return roles.isEmpty() ? "VIEWER" : roles.get(0).code();
    }
}
