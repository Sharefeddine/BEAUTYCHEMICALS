package com.chemmanager.service;

import java.util.List;

import com.chemmanager.model.User;
import com.chemmanager.security.Role;

/** Users and their role assignments (Users &amp; Permissions module). */
public interface UserService {

    List<User> users();

    List<Role> roles();
}
