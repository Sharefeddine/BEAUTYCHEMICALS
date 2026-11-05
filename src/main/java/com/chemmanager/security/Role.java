package com.chemmanager.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A named bundle of permissions. Roles are data (seeded in MySQL, editable in the
 * Users &amp; Permissions module) — the enum-like defaults in {@link DefaultRoles}
 * only provide sane starting points.
 */
public record Role(long id, String code, String nameKey, String descriptionKey,
                   Set<Permission> permissions, boolean systemRole) {

    public Role {
        Objects.requireNonNull(code, "code");
        permissions = permissions == null || permissions.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(Permission.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }

    /** Localised display name resolved lazily by the UI (fallback to the code). */
    public String displayName() {
        String translated = com.chemmanager.localization.I18n.get().t(nameKey);
        return translated.startsWith("!") ? code : translated;
    }
}
