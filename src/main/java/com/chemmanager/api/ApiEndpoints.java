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
    public static final String FORMULATIONS = "/api/formulations";
    public static final String INVENTORY_WAREHOUSES = "/api/inventory/warehouses";
    public static final String INVENTORY_SUMMARY = "/api/inventory/summary";
    public static final String INVENTORY_STOCK = "/api/inventory/stock";
    public static final String INVENTORY_MOVEMENTS = "/api/inventory/movements";
    public static final String INVENTORY_FEFO = "/api/inventory/fefo";
    public static final String INVENTORY_ADJUST = "/api/inventory/adjust";
    public static final String INVENTORY_TRANSFER = "/api/inventory/transfer";
    public static final String SUPPLIERS = "/api/suppliers";
    public static final String PURCHASE_ORDERS = "/api/purchaseOrders";
    public static final String PRODUCTION_VERSIONS = "/api/production/versions";
    public static final String PRODUCTION_ORDERS = "/api/production/orders";
    public static final String NOTIFICATIONS = "/api/notifications";
    public static final String NOTIFICATIONS_READ = "/api/notifications/read";
    public static final String NOTIFICATIONS_READ_ALL = "/api/notifications/readAll";
    public static final String USERS = "/api/users";
    public static final String ROLES = "/api/roles";
    public static final String PERMISSIONS = "/api/permissions";
    public static final String SETTINGS = "/api/settings";
    public static final String COMPANY = "/api/settings/company";
    public static final String AUDIT_LOGS = "/api/auditLogs";
    public static final String CUSTOMERS = "/api/customers";
    public static final String CUSTOMERS_SELECTABLE = "/api/customers/selectable";
    public static final String QUOTATIONS = "/api/quotations";
    public static final String SALES_ORDERS = "/api/salesOrders";
    public static final String DELIVERIES = "/api/deliveries";
    public static final String INVOICES = "/api/invoices";
    public static final String PAYMENTS = "/api/payments";
    public static final String CREDIT_NOTES = "/api/creditNotes";
    public static final String CASH_ACCOUNTS = "/api/cashAccounts";
    public static final String VEHICLES = "/api/vehicles";
    public static final String DRIVERS = "/api/drivers";
    public static final String ROUTES = "/api/routes";
    public static final String FINANCE_PERIODS = "/api/finance/periods";
    public static final String FINANCE_CASHBOOK = "/api/finance/cashbook";
    public static final String FINANCE_SUMMARY = "/api/finance/summary";
    public static final String FINANCE_CASH_TRANSACTIONS = "/api/finance/cashTransactions";
    public static final String EXPENSE_CATEGORIES = "/api/expenseCategories";
    public static final String EXPENSES = "/api/expenses";
    public static final String EXPENSES_SUMMARY = "/api/expenses/summary";
    public static final String DEPARTMENTS = "/api/departments";
    public static final String EMPLOYEES = "/api/employees";
    public static final String LEAVE_REQUESTS = "/api/leaveRequests";
    public static final String SALARY_PAYMENTS = "/api/salaryPayments";
    public static final String REPORTS_MARGIN = "/api/reports/margin";
    public static final String REPORTS_CASHFLOW = "/api/reports/cashflow";
    public static final String REPORTS_RESULT = "/api/reports/result";
    public static final String REPORTS_CATALOGUE = "/api/reports/catalogue";
    public static final String REPORTS_SALES = "/api/reports/sales";
    public static final String REPORTS_PURCHASES = "/api/reports/purchases";
    public static final String REPORTS_STOCK = "/api/reports/stock";
    public static final String REPORTS_PRODUCTION = "/api/reports/production";
    public static final String REPORTS_SAFETY = "/api/reports/safety";
    public static final String SAFETY_OVERVIEW = "/api/safety/overview";
    public static final String SAFETY_PICTOGRAMS = "/api/safety/pictograms";
    public static final String SAFETY_MATERIALS = "/api/safety/materials";
    public static final String SAFETY_MATERIAL = "/api/safety/materials";
    public static final String SAFETY_SHEETS = "/api/safety/sheets";
    public static final String SAFETY_INCIDENTS = "/api/safety/incidents";
    public static final String DOCUMENTS = "/api/documents/{type}/{id}";

    private ApiEndpoints() {
    }

    /** A printable document: {@code quotation|order|delivery|invoice|statement}, in any language. */
    public static String document(String type, long id) {
        return DOCUMENTS.replace("{type}", type).replace("{id}", Long.toString(id));
    }
}
