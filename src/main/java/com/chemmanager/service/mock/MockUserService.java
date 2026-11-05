package com.chemmanager.service.mock;

import java.util.List;

import com.chemmanager.model.User;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.security.DefaultRoles;
import com.chemmanager.security.Role;
import com.chemmanager.service.UserService;

/** Users and roles from the demo dataset. */
public final class MockUserService implements UserService {

    private final MockDataStore store = MockDataStore.get();

    @Override
    public List<User> users() {
        return store.users();
    }

    @Override
    public List<Role> roles() {
        return DefaultRoles.all();
    }
}
