package com.chemmanager.service.api;

import java.util.List;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.User;
import com.chemmanager.security.Role;
import com.chemmanager.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;

/** Users and roles served by {@code /users} and {@code /roles}. */
public final class ApiUserService implements UserService {

    private final ApiClient client;

    public ApiUserService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<User> users() {
        return client.get(ApiEndpoints.USERS, new TypeReference<List<User>>() {
        });
    }

    @Override
    public List<Role> roles() {
        return client.get(ApiEndpoints.ROLES, new TypeReference<List<Role>>() {
        });
    }
}
