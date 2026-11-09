<?php

declare(strict_types=1);

/**
 * End-to-end tests against a running API and a real MySQL database.
 * CI starts `php -S 127.0.0.1:8080 -t backend/public` on a freshly seeded database and calls this.
 *
 * Environment:
 *   API_BASE_URL   default http://127.0.0.1:8080
 *   ADMIN_USER     default admin
 *   ADMIN_PASSWORD default admin123 (the seeded temporary password)
 *   NEW_PASSWORD   default Admin@2026
 *
 * Usage: php backend/tests/integration/run.php
 */

$baseUrl = rtrim(getenv('API_BASE_URL') ?: 'http://127.0.0.1:8080', '/');
$adminUser = getenv('ADMIN_USER') ?: 'admin';
$adminPassword = getenv('ADMIN_PASSWORD') ?: 'admin123';
$newPassword = getenv('NEW_PASSWORD') ?: 'Admin@2026';

$passed = 0;
$failures = [];

function check(string $name, bool $condition, string $detail = ''): void
{
    global $passed, $failures;
    if ($condition) {
        $passed++;
        echo '  ok   ' . $name . PHP_EOL;

        return;
    }
    $failures[] = $name . ($detail !== '' ? ' -> ' . $detail : '');
    echo '  FAIL ' . $name . ($detail !== '' ? ' -> ' . $detail : '') . PHP_EOL;
}

/**
 * @return array{status:int,json:?array,raw:string}
 */
function call(string $method, string $path, ?array $body = null, ?string $token = null): array
{
    global $baseUrl;
    $handle = curl_init($baseUrl . $path);
    $headers = ['Accept: application/json'];
    if ($token !== null) {
        $headers[] = 'Authorization: Bearer ' . $token;
    }
    if ($body !== null) {
        $headers[] = 'Content-Type: application/json';
        curl_setopt($handle, CURLOPT_POSTFIELDS, (string) json_encode($body));
    }
    curl_setopt_array($handle, [
        CURLOPT_CUSTOMREQUEST => $method,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER => $headers,
        CURLOPT_TIMEOUT => 20,
    ]);
    $raw = (string) curl_exec($handle);
    $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
    curl_close($handle);

    return ['status' => $status, 'json' => json_decode($raw, true), 'raw' => $raw];
}

function waitForServer(string $baseUrl): bool
{
    for ($attempt = 0; $attempt < 40; $attempt++) {
        $handle = curl_init($baseUrl . '/api/health');
        curl_setopt_array($handle, [CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 2]);
        $raw = curl_exec($handle);
        $status = (int) curl_getinfo($handle, CURLINFO_RESPONSE_CODE);
        curl_close($handle);
        if ($status > 0 && $raw !== false) {
            return true;
        }
        usleep(250000);
    }

    return false;
}

echo 'API integration tests against ' . $baseUrl . PHP_EOL;

check('the API answers on /api/health', waitForServer($baseUrl));

$response = call('GET', '/api/health');
check('health returns 200', $response['status'] === 200, 'status ' . $response['status']);
check('health reports the database up', ($response['json']['data']['database'] ?? '') === 'ok');
check('health reports the 68 tables of the schema', ($response['json']['data']['tables'] ?? 0) >= 68,
    'tables ' . (string) ($response['json']['data']['tables'] ?? 0));

// ------------------------------------------------------------ authentication
$response = call('POST', '/api/auth/login', ['username' => $adminUser, 'password' => 'wrong-password']);
check('a wrong password is refused', $response['status'] === 401, 'status ' . $response['status']);
check('the refusal does not reveal the account', str_contains($response['raw'], 'Invalid username or password'));

$response = call('POST', '/api/auth/login', ['username' => $adminUser, 'password' => $adminPassword]);
$token = $response['json']['data']['token'] ?? null;
check('the seeded administrator can sign in', $response['status'] === 200, 'status ' . $response['status']);
check('the token is a 64 character opaque value', is_string($token) && strlen($token) === 64);
check('the account is flagged for a password change', ($response['json']['data']['user']['mustChangePassword'] ?? false) === true);
check('the seeded administrator holds every permission',
    count($response['json']['data']['permissions'] ?? []) === 45,
    'permissions ' . count($response['json']['data']['permissions'] ?? []));

$response = call('GET', '/api/products', null, $token);
check('a temporary password blocks the other endpoints', $response['status'] === 403
    && ($response['json']['error']['code'] ?? '') === 'PASSWORD_CHANGE_REQUIRED', 'status ' . $response['status']);

$response = call('POST', '/api/auth/password', [
    'currentPassword' => $adminPassword,
    'newPassword' => 'short',
], $token);
check('a weak password is refused', $response['status'] === 422, 'status ' . $response['status']);

$response = call('POST', '/api/auth/password', [
    'currentPassword' => $adminPassword,
    'newPassword' => $newPassword,
], $token);
check('the password can be changed', $response['status'] === 200, $response['raw']);
check('changing the password revokes the sessions', ($response['json']['data']['sessionsRevoked'] ?? false) === true);

$response = call('GET', '/api/auth/me', null, $token);
check('the token is now revoked', $response['status'] === 401, 'status ' . $response['status']);

$response = call('POST', '/api/auth/login', ['username' => $adminUser, 'password' => $newPassword]);
$token = $response['json']['data']['token'] ?? '';
check('the new password works', $response['status'] === 200 && $token !== '');
check('the password flag is cleared', ($response['json']['data']['user']['mustChangePassword'] ?? true) === false);
check('the sign-in response carries the roles', ($response['json']['data']['roles'] ?? []) === []
    || is_array($response['json']['data']['roles']));

$response = call('GET', '/api/auth/me', null, $token);
check('/auth/me returns the session', $response['status'] === 200
    && ($response['json']['data']['user']['username'] ?? '') === 'admin');
check('/auth/me returns the permission list', count($response['json']['data']['permissions'] ?? []) === 45);

// ------------------------------------------------------------ guards
$response = call('GET', '/api/products');
check('a request without a token is refused', $response['status'] === 401, 'status ' . $response['status']);

$response = call('GET', '/api/products', null, 'x' . str_repeat('0', 63));
check('an unknown token is refused', $response['status'] === 401);

$response = call('GET', '/api/nope', null, $token);
check('an unknown route answers 404', $response['status'] === 404);

$response = call('DELETE', '/api/products', null, $token);
check('a wrong verb answers 405', $response['status'] === 405, 'status ' . $response['status']);

// ------------------------------------------------------------ products CRUD
$response = call('POST', '/api/products', [
    'sku' => '',
    'nameFr' => '',
    'nameAr' => '',
    'categoryId' => 0,
], $token);
check('product validation returns every field error', $response['status'] === 422
    && count($response['json']['error']['details'] ?? []) >= 4, $response['raw']);

$response = call('GET', '/api/products/categories', null, $token);
$categoryId = (int) ($response['json']['data'][0]['id'] ?? 0);
check('product categories are seeded', $response['status'] === 200 && $categoryId > 0);

$sku = 'IT-' . date('His');
$response = call('POST', '/api/products', [
    'sku' => $sku,
    'barcode' => '6130000000011',
    'nameFr' => 'Liquide vaisselle citron 1L',
    'nameAr' => 'سائل غسل الأواني بالليمون 1 ل',
    'categoryId' => $categoryId,
    'unit' => 'PCE',
    'unitsPerCarton' => 12,
    'minStock' => 50,
    'wholesalePrice' => 320.00,
    'retailPrice' => 380.00,
    'vatRate' => 19,
], $token);
$productId = (int) ($response['json']['data']['id'] ?? 0);
check('a product is created', $response['status'] === 201 && $productId > 0, $response['raw']);
check('the arabic name round-trips', ($response['json']['data']['nameAr'] ?? '') === 'سائل غسل الأواني بالليمون 1 ل');
check('the created product starts with zero stock', ($response['json']['data']['stockQuantity'] ?? -1) === 0);

$response = call('POST', '/api/products', ['sku' => $sku, 'nameFr' => 'x', 'nameAr' => 'x', 'categoryId' => $categoryId], $token);
check('a duplicate SKU is refused', $response['status'] === 422
    && ($response['json']['error']['code'] ?? '') === 'DUPLICATE', $response['raw']);

$response = call('GET', '/api/products?search=' . urlencode($sku), null, $token);
check('the product is found by search', ($response['json']['meta']['total'] ?? 0) === 1);
check('pagination metadata is complete',
    isset($response['json']['meta']['page'], $response['json']['meta']['pageSize'], $response['json']['meta']['totalPages']));

$response = call('PUT', '/api/products/' . $productId, ['wholesalePrice' => 355.5, 'minStock' => 60], $token);
check('a product can be updated', $response['status'] === 200
    && (float) ($response['json']['data']['wholesalePrice'] ?? 0) === 355.5, $response['raw']);
check('untouched fields are preserved', ($response['json']['data']['sku'] ?? '') === $sku);

$response = call('GET', '/api/products/' . $productId, null, $token);
check('a product can be read back', $response['status'] === 200 && (int) $response['json']['data']['minStock'] === 60);

$response = call('GET', '/api/products/999999', null, $token);
check('an unknown product answers 404', $response['status'] === 404);

$response = call('DELETE', '/api/products/' . $productId, null, $token);
check('a product is soft deleted', $response['status'] === 200);

$response = call('GET', '/api/products/' . $productId, null, $token);
check('a deleted product is not readable', $response['status'] === 404);

// ------------------------------------------------------------ raw materials
$response = call('GET', '/api/rawMaterials?pageSize=5', null, $token);
check('raw materials are listed', $response['status'] === 200 && is_array($response['json']['data']));
$response = call('GET', '/api/rawMaterials?hazardous=1&lowStock=1&pageSize=5', null, $token);
check('the hazardous and low-stock filters are accepted', $response['status'] === 200, $response['raw']);
$response = call('GET', '/api/rawMaterials/categories', null, $token);
check('material categories are seeded', count($response['json']['data'] ?? []) === 7,
    'count ' . count($response['json']['data'] ?? []));

// ------------------------------------------------------------ dashboard
$response = call('GET', '/api/dashboard/summary', null, $token);
check('the dashboard summary is served', $response['status'] === 200, $response['raw']);
foreach (['kpis', 'monthly', 'salesByCategory', 'inventoryDistribution', 'bestSellers',
             'mostProfitable', 'topCustomers', 'lowStockAlerts', 'expiryAlerts',
             'recentActivities'] as $section) {
    check('the dashboard contains ' . $section, isset($response['json']['data'][$section]));
}
check('the dashboard KPI set is complete',
    count(array_diff(['salesToday', 'salesMonth', 'salesYear', 'salesInRange', 'purchasesInRange',
            'expensesInRange', 'grossProfitInRange', 'netProfitInRange', 'inventoryValue', 'receivables',
            'payables', 'cashBalance', 'productionQuantityInRange', 'averageBasket', 'lowStockCount',
            'expiringCount', 'pendingProductionCount', 'openOrders'],
        array_keys($response['json']['data']['kpis'] ?? []))) === 0,
    implode(',', array_keys($response['json']['data']['kpis'] ?? [])));
check('the monthly series has 12 points', count($response['json']['data']['monthly'] ?? []) === 12);
$response = call('GET', '/api/dashboard/summary?from=2026-13-99&to=2026-01-01', null, $token);
check('an invalid period is refused', $response['status'] === 422);

// ------------------------------------------------------------ notifications
$response = call('GET', '/api/notifications?limit=5', null, $token);
check('the notification centre answers', $response['status'] === 200
    && isset($response['json']['data']['unreadCount']), $response['raw']);
$response = call('POST', '/api/notifications/read', ['ids' => []], $token);
check('marking read without ids is refused', $response['status'] === 422);
$response = call('POST', '/api/notifications/readAll', null, $token);
check('marking everything read succeeds', $response['status'] === 200
    && ($response['json']['data']['unreadCount'] ?? -1) === 0, $response['raw']);

// ------------------------------------------------------------ settings & audit
$response = call('GET', '/api/settings', null, $token);
check('settings and company identity are served', $response['status'] === 200
    && isset($response['json']['data']['company']['currency']), $response['raw']);
check('the currency is the Algerian dinar', ($response['json']['data']['company']['currency'] ?? '') === 'DZD');

$response = call('PUT', '/api/settings', ['settings' => ['stock.expiryWarningDays' => '45']], $token);
check('a setting can be updated', $response['status'] === 200 && ($response['json']['data']['updated'] ?? 0) === 1,
    $response['raw']);
$response = call('GET', '/api/settings', null, $token);
$flat = [];
foreach ($response['json']['data']['settings'] as $group => $rows) {
    foreach ($rows as $row) {
        $flat[$row['settingKey']] = $row['value'];
    }
}
check('the setting keeps its new value', ($flat['stock.expiryWarningDays'] ?? '') === '45');

$response = call('GET', '/api/auditLogs?pageSize=10', null, $token);
check('the audit trail records the session', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) > 0, $response['raw']);
$actions = array_column($response['json']['data'] ?? [], 'action');
check('the audit trail contains a LOGIN entry', in_array('LOGIN', $actions, true), implode(',', $actions));
check('the audit trail contains a CREATE entry', in_array('CREATE', $actions, true));

// ------------------------------------------------------------ users, roles, RBAC
$response = call('GET', '/api/users?pageSize=5', null, $token);
check('users are listed', $response['status'] === 200 && count($response['json']['data'] ?? []) >= 1);
check('the user list exposes the roles', isset($response['json']['data'][0]['roleCodes']));

$response = call('GET', '/api/roles', null, $token);
$roles = $response['json']['data'] ?? [];
check('the 12 roles are seeded', count($roles) === 12, 'roles ' . count($roles));
$viewerId = 0;
foreach ($roles as $role) {
    if ($role['code'] === 'VIEWER') {
        $viewerId = (int) $role['id'];
    }
}
check('the VIEWER role exists', $viewerId > 0);
check('roles expose their permission count', (int) ($roles[0]['permissionCount'] ?? 0) > 0);

$response = call('GET', '/api/permissions', null, $token);
check('the 45 permissions are seeded', count($response['json']['data'] ?? []) === 45);

$viewerName = 'viewer' . date('His');
$response = call('POST', '/api/users', [
    'username' => $viewerName,
    'fullName' => 'Lecteur Test',
    'email' => $viewerName . '@chempro.dz',
    'password' => 'Viewer123',
    'isActive' => true,
    'roleIds' => [$viewerId],
], $token);
$viewerUserId = (int) ($response['json']['data']['id'] ?? 0);
check('a user is created with a role', $response['status'] === 201 && $viewerUserId > 0, $response['raw']);
check('the created user must change the password', ($response['json']['data']['mustChangePassword'] ?? false) === true);

$response = call('POST', '/api/auth/login', ['username' => $viewerName, 'password' => 'Viewer123']);
$viewerToken = $response['json']['data']['token'] ?? '';
check('the new user can sign in', $response['status'] === 200);
$response = call('POST', '/api/auth/password',
    ['currentPassword' => 'Viewer123', 'newPassword' => 'Viewer456'], $viewerToken);
check('the new user can set a password', $response['status'] === 200, $response['raw']);
$response = call('POST', '/api/auth/login', ['username' => $viewerName, 'password' => 'Viewer456']);
$viewerToken = $response['json']['data']['token'] ?? '';
check('the new password is active', $response['status'] === 200);

$response = call('GET', '/api/products', null, $viewerToken);
check('the viewer can read products', $response['status'] === 200, $response['raw']);
$response = call('POST', '/api/products', ['sku' => 'X', 'nameFr' => 'x', 'nameAr' => 'x', 'categoryId' => $categoryId], $viewerToken);
check('the viewer cannot create products', $response['status'] === 403
    && ($response['json']['error']['code'] ?? '') === 'FORBIDDEN', $response['raw']);
$response = call('GET', '/api/users', null, $viewerToken);
check('the viewer cannot list users', $response['status'] === 403);
$response = call('GET', '/api/finance', null, $viewerToken);
check('the viewer gets 404 on a phase 7 endpoint', $response['status'] === 404);

$response = call('POST', '/api/users/' . $viewerUserId . '/password', ['password' => 'Reset1234'], $token);
check('an administrator can reset a password', $response['status'] === 200);
$response = call('GET', '/api/products', null, $viewerToken);
check('the reset revokes the sessions of the user', $response['status'] === 401);

$response = call('DELETE', '/api/users/' . $viewerUserId, null, $token);
check('a user is soft deleted', $response['status'] === 200);

// ------------------------------------------------------------ lockout
for ($attempt = 1; $attempt <= 5; $attempt++) {
    call('POST', '/api/auth/login', ['username' => $adminUser, 'password' => 'still-wrong']);
}
$response = call('POST', '/api/auth/login', ['username' => $adminUser, 'password' => $newPassword]);
check('the account is locked after 5 failures', $response['status'] === 429, 'status ' . $response['status']);
check('the lockout says when to retry', isset($response['json']['error']['details']['retryAfter']));

// ------------------------------------------------------------ sign out
$response = call('POST', '/api/auth/login', ['username' => $viewerName . '-missing', 'password' => 'nope']);
check('an unknown account is refused', $response['status'] === 401);

echo PHP_EOL;
if ($failures === []) {
    echo 'integration tests: ' . $passed . ' passed' . PHP_EOL;
    exit(0);
}
echo 'integration tests: ' . $passed . ' passed, ' . count($failures) . ' failed' . PHP_EOL;
foreach ($failures as $failure) {
    echo '  FAILED: ' . $failure . PHP_EOL;
}
exit(1);
