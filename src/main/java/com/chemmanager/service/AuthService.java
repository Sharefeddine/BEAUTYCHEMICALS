package com.chemmanager.service;

import com.chemmanager.security.AuthException;
import com.chemmanager.security.Session;

/** Authentication contract, implemented by the mock (offline) and the PHP API backends. */
public interface AuthService {

    Session login(String username, String password) throws AuthException;

    void logout(Session session);

    /** Human readable label of the active data source, shown in the shell status bar. */
    String dataSourceLabel();
}
