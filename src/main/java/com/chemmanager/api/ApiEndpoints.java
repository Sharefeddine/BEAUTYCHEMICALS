package com.chemmanager.api;

/** Every REST path used by the desktop client, in one place. */
public final class ApiEndpoints {

    public static final String PING = "/ping";
    public static final String LOGIN = "/auth/login";
    public static final String LOGOUT = "/auth/logout";
    public static final String ME = "/auth/me";
    public static final String DASHBOARD = "/dashboard/summary";
    public static final String PRODUCTS = "/products";
    public static final String PRODUCT_CATEGORIES = "/products/categories";
    public static final String RAW_MATERIALS = "/raw-materials";
    public static final String MATERIAL_CATEGORIES = "/raw-materials/categories";
    public static final String NOTIFICATIONS = "/notifications";
    public static final String NOTIFICATIONS_READ = "/notifications/read";
    public static final String USERS = "/users";
    public static final String ROLES = "/roles";
    public static final String SETTINGS = "/settings/company";

    private ApiEndpoints() {
    }
}
