package com.chemmanager.security;

import static com.chemmanager.security.Permission.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Default role catalogue shipped with the product. Mirrors {@code database/seed/002_roles.sql}
 * so that a mock-mode login and an API-mode login expose exactly the same capabilities.
 */
public final class DefaultRoles {

    private DefaultRoles() {
    }

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String GENERAL_MANAGER = "GENERAL_MANAGER";
    public static final String PRODUCTION_MANAGER = "PRODUCTION_MANAGER";
    public static final String PRODUCTION_OPERATOR = "PRODUCTION_OPERATOR";
    public static final String WAREHOUSE_MANAGER = "WAREHOUSE_MANAGER";
    public static final String WAREHOUSE_EMPLOYEE = "WAREHOUSE_EMPLOYEE";
    public static final String SALES_MANAGER = "SALES_MANAGER";
    public static final String SALES_REPRESENTATIVE = "SALES_REPRESENTATIVE";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String PURCHASING_MANAGER = "PURCHASING_MANAGER";
    public static final String DELIVERY_MANAGER = "DELIVERY_MANAGER";
    public static final String VIEWER = "VIEWER";

    public static List<Role> all() {
        List<Role> roles = new ArrayList<>();
        roles.add(superAdmin());
        roles.add(generalManager());
        roles.add(productionManager());
        roles.add(productionOperator());
        roles.add(warehouseManager());
        roles.add(warehouseEmployee());
        roles.add(salesManager());
        roles.add(salesRepresentative());
        roles.add(accountant());
        roles.add(purchasingManager());
        roles.add(deliveryManager());
        roles.add(viewer());
        return roles;
    }

    public static Role superAdmin() {
        return role(1, SUPER_ADMIN, EnumSet.allOf(Permission.class));
    }

    public static Role generalManager() {
        Set<Permission> p = readAccess();
        p.addAll(EnumSet.of(
                PRODUCTS_PRICE_EDIT, FORMULATIONS_APPROVE, PRODUCTION_APPROVE, PURCHASING_APPROVE,
                SALES_DISCOUNT, FINANCE_PAYMENT, FINANCE_CLOSE_PERIOD, REPORTS_EXPORT, REPORTS_PRINT,
                SETTINGS_VIEW, SETTINGS_EDIT, AUDIT_VIEW, USERS_VIEW));
        return role(2, GENERAL_MANAGER, p);
    }

    public static Role productionManager() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, MATERIALS_EDIT,
                FORMULATIONS_VIEW, FORMULATIONS_EDIT, FORMULATIONS_APPROVE,
                PRODUCTION_VIEW, PRODUCTION_CREATE, PRODUCTION_APPROVE,
                INVENTORY_VIEW, INVENTORY_TRANSFER, REPORTS_VIEW, REPORTS_EXPORT,
                SAFETY_VIEW, SAFETY_EDIT, EMPLOYEES_VIEW);
        return role(3, PRODUCTION_MANAGER, p);
    }

    public static Role productionOperator() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, FORMULATIONS_VIEW,
                PRODUCTION_VIEW, PRODUCTION_CREATE, INVENTORY_VIEW, SAFETY_VIEW);
        return role(4, PRODUCTION_OPERATOR, p);
    }

    public static Role warehouseManager() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, MATERIALS_EDIT,
                INVENTORY_VIEW, INVENTORY_ADJUST, INVENTORY_TRANSFER,
                PURCHASING_VIEW, PURCHASING_CREATE, SUPPLIERS_VIEW,
                SALES_VIEW, CUSTOMERS_VIEW, DISTRIBUTION_VIEW, DISTRIBUTION_MANAGE,
                REPORTS_VIEW, REPORTS_EXPORT, SAFETY_VIEW);
        return role(5, WAREHOUSE_MANAGER, p);
    }

    public static Role warehouseEmployee() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, INVENTORY_VIEW,
                INVENTORY_TRANSFER, DISTRIBUTION_VIEW, SAFETY_VIEW);
        return role(6, WAREHOUSE_EMPLOYEE, p);
    }

    public static Role salesManager() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, PRODUCTS_PRICE_EDIT, MATERIALS_VIEW,
                INVENTORY_VIEW, SALES_VIEW, SALES_CREATE, SALES_INVOICE, SALES_DISCOUNT,
                CUSTOMERS_VIEW, DISTRIBUTION_VIEW, DISTRIBUTION_MANAGE,
                FINANCE_VIEW, FINANCE_PAYMENT, REPORTS_VIEW, REPORTS_EXPORT, REPORTS_PRINT,
                EMPLOYEES_VIEW);
        return role(7, SALES_MANAGER, p);
    }

    public static Role salesRepresentative() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, INVENTORY_VIEW, SALES_VIEW, SALES_CREATE,
                SALES_INVOICE, CUSTOMERS_VIEW, DISTRIBUTION_VIEW, REPORTS_VIEW);
        return role(8, SALES_REPRESENTATIVE, p);
    }

    public static Role accountant() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, INVENTORY_VIEW,
                SALES_VIEW, SALES_INVOICE, CUSTOMERS_VIEW, PURCHASING_VIEW, SUPPLIERS_VIEW,
                FINANCE_VIEW, FINANCE_PAYMENT, FINANCE_CLOSE_PERIOD, EXPENSES_VIEW, EXPENSES_CREATE,
                EMPLOYEES_VIEW, REPORTS_VIEW, REPORTS_EXPORT, REPORTS_PRINT, AUDIT_VIEW);
        return role(9, ACCOUNTANT, p);
    }

    public static Role purchasingManager() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, MATERIALS_EDIT,
                INVENTORY_VIEW, PURCHASING_VIEW, PURCHASING_CREATE, PURCHASING_APPROVE,
                SUPPLIERS_VIEW, FINANCE_VIEW, REPORTS_VIEW, REPORTS_EXPORT, SAFETY_VIEW);
        return role(10, PURCHASING_MANAGER, p);
    }

    public static Role deliveryManager() {
        Set<Permission> p = EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, INVENTORY_VIEW, SALES_VIEW, CUSTOMERS_VIEW,
                DISTRIBUTION_VIEW, DISTRIBUTION_MANAGE, FINANCE_VIEW, EXPENSES_VIEW,
                REPORTS_VIEW, REPORTS_EXPORT);
        return role(11, DELIVERY_MANAGER, p);
    }

    public static Role viewer() {
        return role(12, VIEWER, readAccess());
    }

    private static Set<Permission> readAccess() {
        return EnumSet.of(
                DASHBOARD_VIEW, PRODUCTS_VIEW, MATERIALS_VIEW, FORMULATIONS_VIEW,
                PRODUCTION_VIEW, INVENTORY_VIEW, PURCHASING_VIEW, SUPPLIERS_VIEW,
                SALES_VIEW, CUSTOMERS_VIEW, DISTRIBUTION_VIEW, FINANCE_VIEW,
                EXPENSES_VIEW, EMPLOYEES_VIEW, REPORTS_VIEW, SAFETY_VIEW);
    }

    private static Role role(int id, String code, Set<Permission> permissions) {
        String prefix = "role." + code.toLowerCase();
        return new Role(id, code, prefix, prefix + ".description", permissions, true);
    }
}
