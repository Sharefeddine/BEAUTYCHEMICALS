package com.chemmanager.navigation;

import com.chemmanager.component.Icons;
import com.chemmanager.security.Permission;

/**
 * The 18 functional modules of the sidebar. Each entry knows its FXML screen, the permission
 * required to open it and the delivery phase it belongs to.
 */
public enum NavigationModule {

    DASHBOARD("dashboard", NavGroup.OVERVIEW, Icons.Icon.DASHBOARD, "/fxml/dashboard.fxml",
            Permission.DASHBOARD_VIEW, 1, true),
    PRODUCTS("products", NavGroup.CATALOG, Icons.Icon.PRODUCTS, "/fxml/products.fxml",
            Permission.PRODUCTS_VIEW, 1, true),
    RAW_MATERIALS("raw-materials", NavGroup.CATALOG, Icons.Icon.MATERIALS, "/fxml/raw-materials.fxml",
            Permission.MATERIALS_VIEW, 1, true),
    FORMULATIONS("formulations", NavGroup.CATALOG, Icons.Icon.FORMULATIONS, "/fxml/formulations.fxml",
            Permission.FORMULATIONS_VIEW, 3, true),
    PRODUCTION("production", NavGroup.OPERATIONS, Icons.Icon.PRODUCTION, "/fxml/production.fxml",
            Permission.PRODUCTION_VIEW, 5, true),
    INVENTORY("inventory", NavGroup.OPERATIONS, Icons.Icon.INVENTORY, "/fxml/inventory.fxml",
            Permission.INVENTORY_VIEW, 4, true),
    PURCHASING("purchasing", NavGroup.COMMERCE, Icons.Icon.PURCHASING, "/fxml/purchasing.fxml",
            Permission.PURCHASING_VIEW, 4, true),
    SUPPLIERS("suppliers", NavGroup.COMMERCE, Icons.Icon.SUPPLIERS, "/fxml/suppliers.fxml",
            Permission.SUPPLIERS_VIEW, 4, true),
    SALES("sales", NavGroup.COMMERCE, Icons.Icon.SALES, "/fxml/sales.fxml",
            Permission.SALES_VIEW, 6, false),
    CUSTOMERS("customers", NavGroup.COMMERCE, Icons.Icon.CUSTOMERS, "/fxml/customers.fxml",
            Permission.CUSTOMERS_VIEW, 6, false),
    DISTRIBUTION("distribution", NavGroup.COMMERCE, Icons.Icon.DISTRIBUTION, "/fxml/distribution.fxml",
            Permission.DISTRIBUTION_VIEW, 6, false),
    FINANCE("finance", NavGroup.FINANCE, Icons.Icon.FINANCE, "/fxml/finance.fxml",
            Permission.FINANCE_VIEW, 7, false),
    EXPENSES("expenses", NavGroup.FINANCE, Icons.Icon.EXPENSES, "/fxml/expenses.fxml",
            Permission.EXPENSES_VIEW, 7, false),
    EMPLOYEES("employees", NavGroup.ORGANISATION, Icons.Icon.EMPLOYEES, "/fxml/employees.fxml",
            Permission.EMPLOYEES_VIEW, 7, false),
    REPORTS("reports", NavGroup.INSIGHTS, Icons.Icon.REPORTS, "/fxml/reports.fxml",
            Permission.REPORTS_VIEW, 8, false),
    SAFETY("safety", NavGroup.INSIGHTS, Icons.Icon.SAFETY, "/fxml/safety.fxml",
            Permission.SAFETY_VIEW, 8, false),
    SETTINGS("settings", NavGroup.SYSTEM, Icons.Icon.SETTINGS, "/fxml/settings.fxml",
            Permission.SETTINGS_VIEW, 1, true),
    USERS("users", NavGroup.SYSTEM, Icons.Icon.USERS, "/fxml/users.fxml",
            Permission.USERS_VIEW, 1, true);

    private final String id;
    private final NavGroup group;
    private final Icons.Icon icon;
    private final String fxmlPath;
    private final Permission permission;
    private final int phase;
    private final boolean implemented;

    NavigationModule(String id, NavGroup group, Icons.Icon icon, String fxmlPath, Permission permission,
                     int phase, boolean implemented) {
        this.id = id;
        this.group = group;
        this.icon = icon;
        this.fxmlPath = fxmlPath;
        this.permission = permission;
        this.phase = phase;
        this.implemented = implemented;
    }

    public String id() {
        return id;
    }

    public NavGroup group() {
        return group;
    }

    public Icons.Icon icon() {
        return icon;
    }

    public String fxmlPath() {
        return fxmlPath;
    }

    public Permission permission() {
        return permission;
    }

    public int phase() {
        return phase;
    }

    public boolean implemented() {
        return implemented;
    }

    public String titleKey() {
        return "nav." + id.replace('-', '_');
    }

    public String subtitleKey() {
        return titleKey() + ".subtitle";
    }

    public static NavigationModule fromId(String id) {
        for (NavigationModule module : values()) {
            if (module.id.equalsIgnoreCase(id)) {
                return module;
            }
        }
        return DASHBOARD;
    }
}
