<?php

declare(strict_types=1);

use ChemManager\Api\Controllers\AuditController;
use ChemManager\Api\Controllers\AuthController;
use ChemManager\Api\Controllers\BillingController;
use ChemManager\Api\Controllers\CustomerController;
use ChemManager\Api\Controllers\DashboardController;
use ChemManager\Api\Controllers\DistributionController;
use ChemManager\Api\Controllers\EmployeeController;
use ChemManager\Api\Controllers\ExpenseController;
use ChemManager\Api\Controllers\FinanceController;
use ChemManager\Api\Controllers\DocumentController;
use ChemManager\Api\Controllers\FormulationController;
use ChemManager\Api\Controllers\HealthController;
use ChemManager\Api\Controllers\InventoryController;
use ChemManager\Api\Controllers\MaterialController;
use ChemManager\Api\Controllers\NotificationController;
use ChemManager\Api\Controllers\ProductController;
use ChemManager\Api\Controllers\ProductionController;
use ChemManager\Api\Controllers\PurchaseController;
use ChemManager\Api\Controllers\ReportController;
use ChemManager\Api\Controllers\SalesController;
use ChemManager\Api\Controllers\SettingsController;
use ChemManager\Api\Controllers\SupplierController;
use ChemManager\Api\Controllers\UserController;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Router;
use ChemManager\Api\Documents\DocumentRenderer;
use ChemManager\Api\Http\App;

/**
 * The route table is the single place where a permission is attached to an endpoint, so the
 * client's navigation and the server's guard can never disagree.
 *
 * @param Router $router
 * @param App $app
 */
return static function (Router $router, App $app): void {
    $authController = new AuthController($app);
    $userController = new UserController($app);
    $productController = new ProductController($app);
    $formulationController = new FormulationController($app);
    $materialController = new MaterialController($app);
    $inventoryController = new InventoryController($app);
    $supplierController = new SupplierController($app);
    $purchaseController = new PurchaseController($app);
    $productionController = new ProductionController($app);
    $settingsController = new SettingsController($app);
    $dashboardController = new DashboardController($app);
    $auditController = new AuditController($app);
    $notificationController = new NotificationController($app);
    $customerController = new CustomerController($app);
    $salesController = new SalesController($app);
    $billingController = new BillingController($app);
    $distributionController = new DistributionController($app);
    $financeController = new FinanceController($app);
    $expenseController = new ExpenseController($app);
    $employeeController = new EmployeeController($app);
    $reportController = new ReportController($app);
    $documentController = new DocumentController($app, new DocumentRenderer());

    // ------------------------------------------------------------------ public
    $router->add('GET', '/api/health', static fn (): \ChemManager\Api\Core\Response =>
        (new HealthController($app))->index(new Request('GET', '/api/health')), null, false);
    $router->add('POST', '/api/auth/login', [$authController, 'login'], null, false);

    // ------------------------------------------------------------------ authentication
    $router->add('GET', '/api/auth/me', [$authController, 'me']);
    $router->add('POST', '/api/auth/logout', [$authController, 'logout']);
    $router->add('POST', '/api/auth/password', [$authController, 'changePassword']);

    // ------------------------------------------------------------------ notifications
    $router->add('GET', '/api/notifications', [$notificationController, 'index']);
    $router->add('POST', '/api/notifications/read', [$notificationController, 'markRead']);
    $router->add('POST', '/api/notifications/readAll', [$notificationController, 'markAllRead']);

    // ------------------------------------------------------------------ dashboard
    $router->add('GET', '/api/dashboard/summary', [$dashboardController, 'summary'], 'DASHBOARD_VIEW');

    // ------------------------------------------------------------------ products
    $router->add('GET', '/api/products', [$productController, 'index'], 'PRODUCTS_VIEW');
    $router->add('GET', '/api/products/categories', [$productController, 'categories'], 'PRODUCTS_VIEW');
    $router->add('GET', '/api/products/{id}', [$productController, 'show'], 'PRODUCTS_VIEW');
    $router->add('POST', '/api/products', [$productController, 'store'], 'PRODUCTS_CREATE');
    $router->add('PUT', '/api/products/{id}', [$productController, 'update'], 'PRODUCTS_EDIT');
    $router->add('DELETE', '/api/products/{id}', [$productController, 'destroy'], 'PRODUCTS_DELETE');

    // ------------------------------------------------------------------ raw materials
    $router->add('GET', '/api/rawMaterials', [$materialController, 'index'], 'MATERIALS_VIEW');
    $router->add('GET', '/api/rawMaterials/categories', [$materialController, 'categories'], 'MATERIALS_VIEW');
    $router->add('GET', '/api/rawMaterials/{id}', [$materialController, 'show'], 'MATERIALS_VIEW');
    $router->add('GET', '/api/rawMaterials/{id}/batches', [$materialController, 'batches'], 'INVENTORY_VIEW');
    $router->add('PUT', '/api/rawMaterials/{id}', [$materialController, 'update'], 'MATERIALS_EDIT');

    // ------------------------------------------------------------------ formulations
    $router->add('GET', '/api/formulations', [$formulationController, 'index'], 'FORMULATIONS_VIEW');
    $router->add('POST', '/api/formulations', [$formulationController, 'store'], 'FORMULATIONS_EDIT');
    $router->add('GET', '/api/formulations/{id}', [$formulationController, 'show'], 'FORMULATIONS_VIEW');
    $router->add('PUT', '/api/formulations/{id}', [$formulationController, 'update'], 'FORMULATIONS_EDIT');
    $router->add('POST', '/api/formulations/{id}/archive', [$formulationController, 'archive'], 'FORMULATIONS_EDIT');
    $router->add('GET', '/api/formulations/{id}/versions', [$formulationController, 'versions'], 'FORMULATIONS_VIEW');
    $router->add('POST', '/api/formulations/{id}/versions', [$formulationController, 'storeVersion'], 'FORMULATIONS_EDIT');
    $router->add('GET', '/api/formulations/{id}/versions/{versionId}', [$formulationController, 'showVersion'], 'FORMULATIONS_VIEW');
    $router->add('PUT', '/api/formulations/{id}/versions/{versionId}', [$formulationController, 'updateVersion'], 'FORMULATIONS_EDIT');
    $router->add('POST', '/api/formulations/{id}/versions/{versionId}/approve', [$formulationController, 'approve'], 'FORMULATIONS_APPROVE');
    $router->add('GET', '/api/formulations/{id}/compare', [$formulationController, 'compare'], 'FORMULATIONS_VIEW');

    // ------------------------------------------------------------------ inventory
    $router->add('GET', '/api/inventory/warehouses', [$inventoryController, 'warehouses'], 'INVENTORY_VIEW');
    $router->add('GET', '/api/inventory/summary', [$inventoryController, 'summary'], 'INVENTORY_VIEW');
    $router->add('GET', '/api/inventory/stock', [$inventoryController, 'stock'], 'INVENTORY_VIEW');
    $router->add('GET', '/api/inventory/movements', [$inventoryController, 'movements'], 'INVENTORY_VIEW');
    $router->add('GET', '/api/inventory/fefo', [$inventoryController, 'fefo'], 'INVENTORY_VIEW');
    $router->add('POST', '/api/inventory/adjust', [$inventoryController, 'adjust'], 'INVENTORY_ADJUST');
    $router->add('POST', '/api/inventory/transfer', [$inventoryController, 'transfer'], 'INVENTORY_TRANSFER');

    // ------------------------------------------------------------------ suppliers
    $router->add('GET', '/api/suppliers', [$supplierController, 'index'], 'SUPPLIERS_VIEW');
    $router->add('GET', '/api/suppliers/{id}', [$supplierController, 'show'], 'SUPPLIERS_VIEW');

    // ------------------------------------------------------------------ purchasing
    $router->add('GET', '/api/purchaseOrders', [$purchaseController, 'index'], 'PURCHASING_VIEW');
    $router->add('GET', '/api/purchaseOrders/{id}', [$purchaseController, 'show'], 'PURCHASING_VIEW');
    $router->add('POST', '/api/purchaseOrders', [$purchaseController, 'store'], 'PURCHASING_CREATE');
    $router->add('POST', '/api/purchaseOrders/{id}/send', [$purchaseController, 'send'], 'PURCHASING_CREATE');
    $router->add('POST', '/api/purchaseOrders/{id}/cancel', [$purchaseController, 'cancel'], 'PURCHASING_CREATE');
    $router->add('POST', '/api/purchaseOrders/{id}/receipts', [$purchaseController, 'receive'], 'PURCHASING_APPROVE');

    // ------------------------------------------------------------------ production
    $router->add('GET', '/api/production/versions', [$productionController, 'versions'], 'PRODUCTION_VIEW');
    $router->add('GET', '/api/production/orders', [$productionController, 'index'], 'PRODUCTION_VIEW');
    $router->add('GET', '/api/production/orders/{id}', [$productionController, 'show'], 'PRODUCTION_VIEW');
    $router->add('GET', '/api/production/orders/{id}/genealogy', [$productionController, 'genealogy'],
        'PRODUCTION_VIEW');
    $router->add('POST', '/api/production/orders', [$productionController, 'store'], 'PRODUCTION_CREATE');
    $router->add('POST', '/api/production/orders/{id}/start', [$productionController, 'start'],
        'PRODUCTION_CREATE');
    $router->add('POST', '/api/production/orders/{id}/consume', [$productionController, 'consume'],
        'PRODUCTION_CREATE');
    $router->add('POST', '/api/production/orders/{id}/complete', [$productionController, 'complete'],
        'PRODUCTION_APPROVE');
    $router->add('POST', '/api/production/orders/{id}/quality', [$productionController, 'quality'],
        'PRODUCTION_APPROVE');
    $router->add('POST', '/api/production/orders/{id}/cancel', [$productionController, 'cancel'],
        'PRODUCTION_CREATE');

    // ------------------------------------------------------------------ users and permissions
    $router->add('GET', '/api/users', [$userController, 'index'], 'USERS_VIEW');
    $router->add('GET', '/api/users/{id}', [$userController, 'show'], 'USERS_VIEW');
    $router->add('POST', '/api/users', [$userController, 'store'], 'USERS_MANAGE');
    $router->add('PUT', '/api/users/{id}', [$userController, 'update'], 'USERS_MANAGE');
    $router->add('DELETE', '/api/users/{id}', [$userController, 'destroy'], 'USERS_MANAGE');
    $router->add('POST', '/api/users/{id}/password', [$userController, 'resetPassword'], 'USERS_MANAGE');
    $router->add('GET', '/api/roles', [$userController, 'roles'], 'USERS_VIEW');
    $router->add('GET', '/api/permissions', [$userController, 'permissions'], 'USERS_VIEW');
    $router->add('GET', '/api/departments', [$userController, 'departments'], 'USERS_VIEW');
    $router->add('GET', '/api/roles/{id}/permissions', [$userController, 'rolePermissions'], 'USERS_VIEW');
    $router->add('PUT', '/api/roles/{id}/permissions', [$userController, 'updateRolePermissions'], 'USERS_MANAGE');

    // ------------------------------------------------------------------ customers
    $router->add('GET', '/api/customers', [$customerController, 'index'], 'CUSTOMERS_VIEW');
    $router->add('GET', '/api/customers/selectable', [$customerController, 'selectable'], 'CUSTOMERS_VIEW');
    $router->add('GET', '/api/customers/{id}', [$customerController, 'show'], 'CUSTOMERS_VIEW');
    $router->add('GET', '/api/customers/{id}/statement', [$customerController, 'statement'], 'CUSTOMERS_VIEW');
    $router->add('POST', '/api/customers', [$customerController, 'store'], 'SALES_CREATE');
    $router->add('PUT', '/api/customers/{id}', [$customerController, 'update'], 'SALES_CREATE');
    $router->add('DELETE', '/api/customers/{id}', [$customerController, 'destroy'], 'SALES_CREATE');

    // ------------------------------------------------------------------ quotations
    $router->add('GET', '/api/quotations', [$salesController, 'quotations'], 'SALES_VIEW');
    $router->add('GET', '/api/quotations/{id}', [$salesController, 'quotation'], 'SALES_VIEW');
    $router->add('POST', '/api/quotations', [$salesController, 'storeQuotation'], 'SALES_CREATE');
    $router->add('POST', '/api/quotations/{id}/send', [$salesController, 'sendQuotation'], 'SALES_CREATE');
    $router->add('POST', '/api/quotations/{id}/accept', [$salesController, 'acceptQuotation'], 'SALES_CREATE');
    $router->add('POST', '/api/quotations/{id}/reject', [$salesController, 'rejectQuotation'], 'SALES_CREATE');

    // ------------------------------------------------------------------ sales orders
    $router->add('GET', '/api/salesOrders', [$salesController, 'orders'], 'SALES_VIEW');
    $router->add('GET', '/api/salesOrders/{id}', [$salesController, 'order'], 'SALES_VIEW');
    $router->add('POST', '/api/salesOrders', [$salesController, 'storeOrder'], 'SALES_CREATE');
    $router->add('POST', '/api/salesOrders/{id}/confirm', [$salesController, 'confirmOrder'], 'SALES_CREATE');
    $router->add('POST', '/api/salesOrders/{id}/cancel', [$salesController, 'cancelOrder'], 'SALES_CREATE');
    $router->add('POST', '/api/salesOrders/{id}/deliveries', [$salesController, 'storeDelivery'], 'SALES_CREATE');

    // ------------------------------------------------------------------ delivery notes
    $router->add('GET', '/api/deliveries', [$salesController, 'deliveries'], 'DISTRIBUTION_VIEW');
    $router->add('GET', '/api/deliveries/{id}', [$salesController, 'delivery'], 'DISTRIBUTION_VIEW');
    $router->add('POST', '/api/deliveries/{id}/load', [$salesController, 'loadDelivery'], 'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/deliveries/{id}/deliver', [$salesController, 'deliverDelivery'],
        'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/deliveries/{id}/return', [$salesController, 'returnDelivery'],
        'DISTRIBUTION_MANAGE');

    // ------------------------------------------------------------------ invoices
    $router->add('GET', '/api/invoices', [$billingController, 'invoices'], 'SALES_VIEW');
    $router->add('GET', '/api/invoices/{id}', [$billingController, 'invoice'], 'SALES_VIEW');
    $router->add('POST', '/api/invoices', [$billingController, 'storeInvoice'], 'SALES_INVOICE');
    $router->add('PUT', '/api/invoices/{id}', [$billingController, 'updateInvoice'], 'SALES_INVOICE');
    $router->add('POST', '/api/invoices/{id}/issue', [$billingController, 'issueInvoice'], 'SALES_INVOICE');
    $router->add('POST', '/api/invoices/{id}/cancel', [$billingController, 'cancelInvoice'], 'SALES_INVOICE');
    $router->add('POST', '/api/invoices/{id}/creditNotes', [$billingController, 'storeCreditNote'],
        'SALES_INVOICE');

    // ------------------------------------------------------------------ payments
    $router->add('GET', '/api/payments', [$billingController, 'payments'], 'FINANCE_VIEW');
    $router->add('POST', '/api/payments', [$billingController, 'storePayment'], 'FINANCE_PAYMENT');
    $router->add('GET', '/api/cashAccounts', [$billingController, 'cashAccounts'], 'FINANCE_VIEW');

    // ------------------------------------------------------------------ credit notes
    $router->add('GET', '/api/creditNotes', [$billingController, 'creditNotes'], 'FINANCE_VIEW');
    $router->add('GET', '/api/creditNotes/{id}', [$billingController, 'creditNote'], 'FINANCE_VIEW');
    $router->add('POST', '/api/creditNotes/{id}/apply', [$billingController, 'applyCreditNote'],
        'FINANCE_PAYMENT');
    $router->add('POST', '/api/creditNotes/{id}/cancel', [$billingController, 'cancelCreditNote'],
        'SALES_INVOICE');

    // ------------------------------------------------------------------ distribution
    $router->add('GET', '/api/vehicles', [$distributionController, 'vehicles'], 'DISTRIBUTION_VIEW');
    $router->add('POST', '/api/vehicles', [$distributionController, 'storeVehicle'], 'DISTRIBUTION_MANAGE');
    $router->add('PUT', '/api/vehicles/{id}', [$distributionController, 'updateVehicle'],
        'DISTRIBUTION_MANAGE');
    $router->add('GET', '/api/drivers', [$distributionController, 'drivers'], 'DISTRIBUTION_VIEW');
    $router->add('GET', '/api/routes', [$distributionController, 'routes'], 'DISTRIBUTION_VIEW');
    $router->add('GET', '/api/routes/{id}', [$distributionController, 'route'], 'DISTRIBUTION_VIEW');
    $router->add('POST', '/api/routes', [$distributionController, 'storeRoute'], 'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/routes/{id}/load', [$distributionController, 'loadRoute'],
        'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/routes/{id}/start', [$distributionController, 'startRoute'],
        'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/routes/{id}/stops/{stopId}/deliver', [$distributionController, 'deliverStop'],
        'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/routes/{id}/complete', [$distributionController, 'completeRoute'],
        'DISTRIBUTION_MANAGE');
    $router->add('POST', '/api/routes/{id}/cancel', [$distributionController, 'cancelRoute'],
        'DISTRIBUTION_MANAGE');

    // ------------------------------------------------------------------ finance
    $router->add('GET', '/api/finance/periods', [$financeController, 'periods'], 'FINANCE_VIEW');
    $router->add('GET', '/api/finance/periods/{id}', [$financeController, 'period'], 'FINANCE_VIEW');
    $router->add('POST', '/api/finance/periods/{id}/close', [$financeController, 'closePeriod'],
        'FINANCE_CLOSE_PERIOD');
    $router->add('POST', '/api/finance/periods/{id}/reopen', [$financeController, 'reopenPeriod'],
        'FINANCE_CLOSE_PERIOD');
    $router->add('GET', '/api/finance/cashbook', [$financeController, 'cashBook'], 'FINANCE_VIEW');
    $router->add('GET', '/api/finance/summary', [$financeController, 'summary'], 'FINANCE_VIEW');
    $router->add('POST', '/api/finance/cashTransactions', [$financeController, 'storeTransaction'],
        'FINANCE_PAYMENT');

    // ------------------------------------------------------------------ expenses
    $router->add('GET', '/api/expenseCategories', [$expenseController, 'categories'], 'EXPENSES_VIEW');
    $router->add('GET', '/api/expenses', [$expenseController, 'expenses'], 'EXPENSES_VIEW');
    $router->add('GET', '/api/expenses/summary', [$expenseController, 'summary'], 'EXPENSES_VIEW');
    $router->add('GET', '/api/expenses/{id}', [$expenseController, 'expense'], 'EXPENSES_VIEW');
    $router->add('POST', '/api/expenses', [$expenseController, 'store'], 'EXPENSES_CREATE');
    $router->add('POST', '/api/expenses/{id}/approve', [$expenseController, 'approve'], 'EXPENSES_CREATE');
    $router->add('POST', '/api/expenses/{id}/pay', [$expenseController, 'pay'], 'FINANCE_PAYMENT');
    $router->add('POST', '/api/expenses/{id}/cancel', [$expenseController, 'cancel'], 'EXPENSES_CREATE');

    // ------------------------------------------------------------------ employees, leave, payroll
    $router->add('GET', '/api/departments', [$employeeController, 'departments'], 'EMPLOYEES_VIEW');
    $router->add('GET', '/api/employees', [$employeeController, 'index'], 'EMPLOYEES_VIEW');
    $router->add('GET', '/api/employees/{id}', [$employeeController, 'show'], 'EMPLOYEES_VIEW');
    $router->add('POST', '/api/employees', [$employeeController, 'store'], 'EMPLOYEES_EDIT');
    $router->add('PUT', '/api/employees/{id}', [$employeeController, 'update'], 'EMPLOYEES_EDIT');
    $router->add('DELETE', '/api/employees/{id}', [$employeeController, 'destroy'], 'EMPLOYEES_EDIT');
    $router->add('GET', '/api/employees/{id}/attendance', [$employeeController, 'attendance'], 'EMPLOYEES_VIEW');
    $router->add('POST', '/api/employees/{id}/attendance', [$employeeController, 'recordAttendance'],
        'EMPLOYEES_EDIT');
    $router->add('GET', '/api/leaveRequests', [$employeeController, 'leaveRequests'], 'EMPLOYEES_VIEW');
    $router->add('POST', '/api/leaveRequests', [$employeeController, 'requestLeave'], 'EMPLOYEES_EDIT');
    $router->add('POST', '/api/leaveRequests/{id}/approve', [$employeeController, 'approveLeave'],
        'EMPLOYEES_EDIT');
    $router->add('POST', '/api/leaveRequests/{id}/reject', [$employeeController, 'rejectLeave'],
        'EMPLOYEES_EDIT');
    $router->add('POST', '/api/leaveRequests/{id}/cancel', [$employeeController, 'cancelLeave'],
        'EMPLOYEES_EDIT');
    $router->add('GET', '/api/salaryPayments', [$employeeController, 'salaryPayments'], 'EMPLOYEES_VIEW');
    $router->add('POST', '/api/salaryPayments/run', [$employeeController, 'runPayroll'], 'EMPLOYEES_EDIT');
    $router->add('POST', '/api/salaryPayments/{id}/pay', [$employeeController, 'paySalary'],
        'FINANCE_PAYMENT');

    // ------------------------------------------------------------------ financial statements
    $router->add('GET', '/api/reports/margin', [$reportController, 'margin'], 'REPORTS_VIEW');
    $router->add('GET', '/api/reports/cashflow', [$reportController, 'cashflow'], 'REPORTS_VIEW');
    $router->add('GET', '/api/reports/result', [$reportController, 'result'], 'REPORTS_VIEW');

    // ------------------------------------------------------------------ documents
    $router->add('GET', '/api/documents/{type}/{id}', [$documentController, 'show'], 'SALES_VIEW');

    // ------------------------------------------------------------------ settings
    $router->add('GET', '/api/settings', [$settingsController, 'index'], 'SETTINGS_VIEW');
    $router->add('PUT', '/api/settings', [$settingsController, 'update'], 'SETTINGS_EDIT');
    $router->add('PUT', '/api/settings/company', [$settingsController, 'updateCompany'], 'SETTINGS_EDIT');

    // ------------------------------------------------------------------ audit
    $router->add('GET', '/api/auditLogs', [$auditController, 'index'], 'AUDIT_VIEW');
};
