package com.chemmanager.api;

/**
 * Every REST path the desktop client may call, in one place.
 *
 * <p>Paths are the routes the PHP API actually serves ({@code backend/src/routes.php}): the
 * configured base URL is the server root, the {@code /api} prefix belongs to the path.
 * {@code ApiContractTest} parses {@code routes.php} and fails the build when a path here no
 * longer exists on the server, so the two halves cannot drift apart silently.</p>
 */
public final class ApiEndpoints {

    public static final String HEALTH = "/api/health";
    public static final String LOGIN = "/api/auth/login";
    public static final String LOGOUT = "/api/auth/logout";
    public static final String ME = "/api/auth/me";
    public static final String PASSWORD = "/api/auth/password";
    public static final String DASHBOARD = "/api/dashboard/summary";
    public static final String PRODUCTS = "/api/products";
    public static final String PRODUCT_CATEGORIES = "/api/products/categories";
    public static final String RAW_MATERIALS = "/api/rawMaterials";
    public static final String MATERIAL_CATEGORIES = "/api/rawMaterials/categories";
    public static final String NOTIFICATIONS = "/api/notifications";
    public static final String NOTIFICATIONS_READ = "/api/notifications/read";
    public static final String NOTIFICATIONS_READ_ALL = "/api/notifications/readAll";
    public static final String USERS = "/api/users";
    public static final String ROLES = "/api/roles";
    public static final String PERMISSIONS = "/api/permissions";
    public static final String SETTINGS = "/api/settings";
    public static final String COMPANY = "/api/settings/company";
    public static final String AUDIT_LOGS = "/api/auditLogs";

    private ApiEndpoints() {
    }
}
