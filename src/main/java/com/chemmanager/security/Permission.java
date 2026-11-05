package com.chemmanager.security;

/**
 * Granular permissions enforced in the UI navigation and (authoritatively) by the PHP API.
 * The {@code PERMISSIONS} matrix in {@code docs/05-security-rbac.md} documents the defaults.
 */
public enum Permission {
    DASHBOARD_VIEW(Module.DASHBOARD, Action.VIEW),
    PRODUCTS_VIEW(Module.PRODUCTS, Action.VIEW),
    PRODUCTS_CREATE(Module.PRODUCTS, Action.CREATE),
    PRODUCTS_EDIT(Module.PRODUCTS, Action.EDIT),
    PRODUCTS_DELETE(Module.PRODUCTS, Action.DELETE),
    PRODUCTS_PRICE_EDIT(Module.PRODUCTS, Action.MODIFY_PRICES),

    MATERIALS_VIEW(Module.MATERIALS, Action.VIEW),
    MATERIALS_EDIT(Module.MATERIALS, Action.EDIT),

    FORMULATIONS_VIEW(Module.FORMULATIONS, Action.VIEW),
    FORMULATIONS_EDIT(Module.FORMULATIONS, Action.EDIT),
    FORMULATIONS_APPROVE(Module.FORMULATIONS, Action.APPROVE),

    PRODUCTION_VIEW(Module.PRODUCTION, Action.VIEW),
    PRODUCTION_CREATE(Module.PRODUCTION, Action.CREATE),
    PRODUCTION_APPROVE(Module.PRODUCTION, Action.APPROVE),

    INVENTORY_VIEW(Module.INVENTORY, Action.VIEW),
    INVENTORY_ADJUST(Module.INVENTORY, Action.ADJUST_INVENTORY),
    INVENTORY_TRANSFER(Module.INVENTORY, Action.EDIT),

    PURCHASING_VIEW(Module.PURCHASING, Action.VIEW),
    PURCHASING_CREATE(Module.PURCHASING, Action.CREATE),
    PURCHASING_APPROVE(Module.PURCHASING, Action.APPROVE),
    SUPPLIERS_VIEW(Module.SUPPLIERS, Action.VIEW),

    SALES_VIEW(Module.SALES, Action.VIEW),
    SALES_CREATE(Module.SALES, Action.CREATE),
    SALES_INVOICE(Module.SALES, Action.PRINT),
    SALES_DISCOUNT(Module.SALES, Action.MODIFY_PRICES),
    CUSTOMERS_VIEW(Module.CUSTOMERS, Action.VIEW),

    DISTRIBUTION_VIEW(Module.DISTRIBUTION, Action.VIEW),
    DISTRIBUTION_MANAGE(Module.DISTRIBUTION, Action.EDIT),

    FINANCE_VIEW(Module.FINANCE, Action.VIEW),
    FINANCE_PAYMENT(Module.FINANCE, Action.MANAGE_PAYMENTS),
    FINANCE_CLOSE_PERIOD(Module.FINANCE, Action.CLOSE_PERIOD),
    EXPENSES_VIEW(Module.EXPENSES, Action.VIEW),
    EXPENSES_CREATE(Module.EXPENSES, Action.CREATE),

    EMPLOYEES_VIEW(Module.EMPLOYEES, Action.VIEW),
    EMPLOYEES_EDIT(Module.EMPLOYEES, Action.EDIT),

    REPORTS_VIEW(Module.REPORTS, Action.VIEW),
    REPORTS_EXPORT(Module.REPORTS, Action.EXPORT),
    REPORTS_PRINT(Module.REPORTS, Action.PRINT),

    SAFETY_VIEW(Module.SAFETY, Action.VIEW),
    SAFETY_EDIT(Module.SAFETY, Action.EDIT),

    SETTINGS_VIEW(Module.SETTINGS, Action.VIEW),
    SETTINGS_EDIT(Module.SETTINGS, Action.EDIT),

    USERS_VIEW(Module.USERS, Action.VIEW),
    USERS_MANAGE(Module.USERS, Action.CREATE),
    AUDIT_VIEW(Module.AUDIT, Action.VIEW);

    /** Functional module a permission belongs to. */
    public enum Module {
        DASHBOARD, PRODUCTS, MATERIALS, FORMULATIONS, PRODUCTION, INVENTORY, PURCHASING,
        SUPPLIERS, SALES, CUSTOMERS, DISTRIBUTION, FINANCE, EXPENSES, EMPLOYEES,
        REPORTS, SAFETY, SETTINGS, USERS, AUDIT
    }

    /** Action a permission grants. */
    public enum Action {
        VIEW, CREATE, EDIT, DELETE, APPROVE, PRINT, EXPORT, MANAGE_PAYMENTS,
        MODIFY_PRICES, ADJUST_INVENTORY, CLOSE_PERIOD
    }

    private final Module module;
    private final Action action;

    Permission(Module module, Action action) {
        this.module = module;
        this.action = action;
    }

    public Module module() {
        return module;
    }

    public Action action() {
        return action;
    }

    public String labelKey() {
        return "permission." + name().toLowerCase();
    }
}
