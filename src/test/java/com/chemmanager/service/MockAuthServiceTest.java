package com.chemmanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chemmanager.security.AuthException;
import com.chemmanager.security.Permission;
import com.chemmanager.security.Session;
import com.chemmanager.service.mock.MockAuthService;
import org.junit.jupiter.api.Test;

/**
 * The offline demo authentication must enforce the same rules as the PHP API:
 * hashed passwords, active accounts, throttling and permission aggregation.
 */
class MockAuthServiceTest {

    private final AuthService auth = new MockAuthService();

    @Test
    void seededAdministratorCanSignIn() {
        Session session = auth.login("admin", "admin123");
        assertNotNull(session.token());
        assertEquals("admin", session.user().username());
        assertTrue(session.permissions().contains(Permission.USERS_MANAGE));
        assertTrue(session.permissions().contains(Permission.SETTINGS_EDIT));
        assertFalse(session.isExpired());
        assertEquals("Yacine Belkacem", session.user().fullName());
    }

    @Test
    void usernameIsCaseInsensitiveAndTrimmed() {
        Session session = auth.login("  ADMIN ", "admin123");
        assertEquals("admin", session.user().username());
    }

    @Test
    void accountantGetsFinanceRightsOnly() {
        Session session = auth.login("accounting", "acc123");
        assertTrue(session.permissions().contains(Permission.FINANCE_VIEW));
        assertFalse(session.permissions().contains(Permission.PRODUCTION_APPROVE));
        assertTrue(session.permissions().contains(Permission.FINANCE_PAYMENT));
    }

    @Test
    void wrongPasswordIsRejected() {
        AuthException ex = assertThrows(AuthException.class, () -> auth.login("sales", "not-the-password"));
        assertEquals("login.error.invalid", ex.messageKey());
    }

    @Test
    void unknownUserIsRejected() {
        assertThrows(AuthException.class, () -> auth.login("nobody", "whatever"));
    }

    @Test
    void emptyCredentialsAreRejected() {
        AuthException ex = assertThrows(AuthException.class, () -> auth.login("", ""));
        assertEquals("login.error.required", ex.messageKey());
        assertThrows(AuthException.class, () -> auth.login("admin", ""));
    }
}
