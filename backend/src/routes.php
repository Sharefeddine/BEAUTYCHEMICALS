<?php

declare(strict_types=1);

use ChemManager\Api\Controllers\AuditController;
use ChemManager\Api\Controllers\AuthController;
use ChemManager\Api\Controllers\DashboardController;
use ChemManager\Api\Controllers\FormulationController;
use ChemManager\Api\Controllers\HealthController;
use ChemManager\Api\Controllers\InventoryController;
use ChemManager\Api\Controllers\MaterialController;
use ChemManager\Api\Controllers\NotificationController;
use ChemManager\Api\Controllers\ProductController;
use ChemManager\Api\Controllers\PurchaseController;
use ChemManager\Api\Controllers\SettingsController;
use ChemManager\Api\Controllers\SupplierController;
use ChemManager\Api\Controllers\UserController;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Router;
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
    $settingsController = new SettingsController($app);
    $dashboardController = new DashboardController($app);
    $auditController = new AuditController($app);
    $notificationController = new NotificationController($app);

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

    // ------------------------------------------------------------------ settings
    $router->add('GET', '/api/settings', [$settingsController, 'index'], 'SETTINGS_VIEW');
    $router->add('PUT', '/api/settings', [$settingsController, 'update'], 'SETTINGS_EDIT');
    $router->add('PUT', '/api/settings/company', [$settingsController, 'updateCompany'], 'SETTINGS_EDIT');

    // ------------------------------------------------------------------ audit
    $router->add('GET', '/api/auditLogs', [$auditController, 'index'], 'AUDIT_VIEW');
};
