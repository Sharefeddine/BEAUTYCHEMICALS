package com.chemmanager.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The twelve seeded roles must match the permission matrix documented in docs/05-security-rbac.md. */
class RolesTest {

    @Test
    void twelveRolesAreSeeded() {
        List<Role> roles = DefaultRoles.all();
        assertEquals(12, roles.size(), "the specification defines twelve roles");
        assertEquals(12, roles.stream().map(Role::code).distinct().count());
    }

    @Test
    void superAdminHoldsEveryPermission() {
        Role superAdmin = DefaultRoles.superAdmin();
        assertEquals(EnumSet.allOf(Permission.class), EnumSet.copyOf(superAdmin.permissions()));
    }

    @Test
    void viewerIsReadOnly() {
        Set<Permission> permissions = DefaultRoles.viewer().permissions();
        assertTrue(permissions.contains(Permission.DASHBOARD_VIEW));
        assertTrue(permissions.contains(Permission.PRODUCTS_VIEW));
        assertFalse(permissions.contains(Permission.PRODUCTS_CREATE));
        assertFalse(permissions.contains(Permission.SALES_CREATE));
        assertFalse(permissions.contains(Permission.USERS_MANAGE));
    }

    @Test
    void operatorsCannotApproveOrDelete() {
        Set<Permission> operator = DefaultRoles.productionOperator().permissions();
        assertTrue(operator.contains(Permission.PRODUCTION_VIEW));
        assertFalse(operator.contains(Permission.FORMULATIONS_APPROVE));
        assertFalse(operator.contains(Permission.PRODUCTION_APPROVE));
        assertFalse(operator.contains(Permission.PRODUCTS_DELETE));
    }

    @Test
    void accountantCannotTouchProductionOrFormulas() {
        Set<Permission> accountant = DefaultRoles.accountant().permissions();
        assertTrue(accountant.contains(Permission.FINANCE_VIEW));
        assertTrue(accountant.contains(Permission.FINANCE_PAYMENT));
        assertFalse(accountant.contains(Permission.FORMULATIONS_EDIT));
        assertFalse(accountant.contains(Permission.PRODUCTION_CREATE));
    }

    @Test
    void sessionAggregatesPermissionsOfEveryRole() {
        var user = new com.chemmanager.model.User(99, "multi", "Multi Role", "m@chempro.dz", "+213 000",
                "Tester", List.of(DefaultRoles.viewer(), DefaultRoles.accountant()), true, null);
        Session session = Session.of(user, java.time.Duration.ofHours(1), "test");
        assertTrue(session.permissions().contains(Permission.DASHBOARD_VIEW));
        assertTrue(session.permissions().contains(Permission.FINANCE_PAYMENT));
        assertFalse(session.permissions().contains(Permission.USERS_MANAGE));
        assertFalse(session.isExpired());
    }
}
