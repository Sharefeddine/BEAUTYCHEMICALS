package com.chemmanager.service.api;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.User;
import com.chemmanager.security.Permission;
import com.chemmanager.security.Role;
import com.chemmanager.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Users and roles served by {@code /api/users}, {@code /api/roles} and {@code /api/permissions}.
 *
 * <p>The role matrix is rebuilt from the three endpoints (roles, permission catalogue, permission
 * ids per role) so the Users &amp; Permissions screen shows what the server actually enforces.</p>
 */
public final class ApiUserService implements UserService {

    private static final int PAGE_SIZE = 200;

    private final ApiClient client;
    private Map<Integer, Permission> permissionById;

    public ApiUserService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<User> users() {
        List<JsonNode> rows = client.get(ApiEndpoints.USERS,
                Map.of("page", "1", "pageSize", Integer.toString(PAGE_SIZE)), new TypeReference<List<JsonNode>>() {
                });
        List<User> users = new ArrayList<>();
        if (rows != null) {
            for (JsonNode row : rows) {
                users.add(ApiMapper.user(row));
            }
        }
        return users;
    }

    @Override
    public List<Role> roles() {
        List<JsonNode> rows = client.get(ApiEndpoints.ROLES, Map.of(), new TypeReference<List<JsonNode>>() {
        });
        List<Role> roles = new ArrayList<>();
        if (rows == null) {
            return roles;
        }
        for (JsonNode row : rows) {
            long id = row.path("id").asLong();
            String code = row.path("code").asText("");
            Set<Permission> permissions = permissionsOf(id);
            roles.add(new Role(id, code, "role." + code.toLowerCase(),
                    "role." + code.toLowerCase() + ".description", permissions,
                    row.path("isSystem").asBoolean(true)));
        }
        return roles;
    }

    /**
     * The permission codes of one role. Permission ids are stable, so the catalogue is fetched once
     * and reused for every role of the matrix.
     */
    private Set<Permission> permissionsOf(long roleId) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        JsonNode payload = client.get(ApiEndpoints.ROLES + "/" + roleId + "/permissions", JsonNode.class);
        if (payload == null) {
            return permissions;
        }
        Map<Integer, Permission> catalogue = permissionCatalogue();
        for (JsonNode id : payload.path("permissionIds")) {
            Permission permission = catalogue.get(id.asInt());
            if (permission != null) {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    private Map<Integer, Permission> permissionCatalogue() {
        if (permissionById == null) {
            List<JsonNode> rows = client.get(ApiEndpoints.PERMISSIONS, Map.of(),
                    new TypeReference<List<JsonNode>>() {
                    });
            Map<Integer, Permission> catalogue = new LinkedHashMap<>();
            if (rows != null) {
                for (JsonNode row : rows) {
                    try {
                        catalogue.put(row.path("id").asInt(), Permission.valueOf(row.path("code").asText()));
                    } catch (IllegalArgumentException ignored) {
                        // a permission the client release does not know yet
                    }
                }
            }
            permissionById = Map.copyOf(catalogue);
        }
        return permissionById;
    }
}
