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

// ------------------------------------------------------------ formulations
$response = call('GET', '/api/rawMaterials?pageSize=10', null, $token);
$materials = $response['json']['data'] ?? [];
$materialA = (int) ($materials[0]['id'] ?? 0);
$materialB = (int) ($materials[1]['id'] ?? 0);
$materialPriceA = (float) ($materials[0]['purchasePrice'] ?? 0);
check('two raw materials are available for a formula', $materialA > 0 && $materialB > 0);

$response = call('POST', '/api/formulations', ['nameFr' => '', 'nameAr' => ''], $token);
check('a formulation without a name is refused', $response['status'] === 422, $response['raw']);

$response = call('POST', '/api/formulations', [
    'nameFr' => 'Gel douche test ' . date('His'),
    'nameAr' => 'جل الاستحمام اختبار',
    'notes' => 'Saisie manuelle par le responsable de production',
], $token);
$formulationId = (int) ($response['json']['data']['id'] ?? 0);
$formulationCode = (string) ($response['json']['data']['code'] ?? '');
check('a formulation is created as a draft', $response['status'] === 201
    && ($response['json']['data']['status'] ?? '') === 'DRAFT' && $formulationId > 0, $response['raw']);
check('the formulation receives a generated code', $formulationCode !== '');

$response = call('GET', '/api/formulations?search=' . urlencode($formulationCode), null, $token);
check('the formulation is listed', ($response['json']['meta']['total'] ?? 0) === 1, $response['raw']);

$response = call('POST', '/api/formulations/' . $formulationId . '/versions', ['instructions' => 'Phase A puis phase B.'], $token);
$versionOneId = (int) ($response['json']['data']['id'] ?? 0);
check('the first version is created', $response['status'] === 201
    && (int) ($response['json']['data']['version'] ?? 0) === 1 && $versionOneId > 0, $response['raw']);
check('a new version starts empty', (int) ($response['json']['data']['itemCount'] ?? -1) === 0);

$response = call('POST', '/api/formulations/' . $formulationId . '/versions/' . $versionOneId . '/approve', null, $token);
check('an empty version cannot be approved', $response['status'] === 422, $response['raw']);

$response = call('PUT', '/api/formulations/' . $formulationId . '/versions/' . $versionOneId, [
    'expectedYield' => 100,
    'yieldUnit' => 'KG',
    'items' => [
        ['materialId' => $materialA, 'quantity' => 60, 'unit' => 'KG', 'phase' => 'A'],
        ['materialId' => $materialB, 'quantity' => 40, 'unit' => 'KG', 'phase' => 'B'],
    ],
], $token);
$percentageSum = 0.0;
foreach ($response['json']['data']['items'] ?? [] as $item) {
    $percentageSum += (float) $item['percentage'];
}
check('the bill of materials is stored as entered', $response['status'] === 200
    && (int) ($response['json']['data']['itemCount'] ?? 0) === 2, $response['raw']);
check('percentages are computed when the units match', abs($percentageSum - 100.0) < 0.01,
    'sum ' . $percentageSum);
check('the cost is computed from the current purchase prices',
    (float) ($response['json']['data']['cost'] ?? 0) > 0, $response['raw']);

$response = call('PUT', '/api/formulations/' . $formulationId . '/versions/' . $versionOneId, [
    'items' => [['materialId' => $materialA, 'quantity' => 0, 'unit' => 'KG']],
], $token);
check('a null quantity is refused', $response['status'] === 422, $response['raw']);

$response = call('POST', '/api/formulations/' . $formulationId . '/versions/' . $versionOneId . '/approve', null, $token);
check('a filled version is approved', $response['status'] === 200
    && ($response['json']['data']['isLocked'] ?? false) === true, $response['raw']);
check('the approval is stamped with the approver',
    ($response['json']['data']['approvedByName'] ?? '') === 'admin', $response['raw']);

$response = call('GET', '/api/formulations/' . $formulationId, null, $token);
check('the formulation becomes APPROVED and points at its version', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'APPROVED'
    && (int) ($response['json']['data']['currentVersionId'] ?? 0) === $versionOneId, $response['raw']);
check('the approved version is served with its items',
    count($response['json']['data']['currentVersion']['items'] ?? []) === 2);

$response = call('PUT', '/api/formulations/' . $formulationId . '/versions/' . $versionOneId, [
    'instructions' => 'modification interdite',
], $token);
check('an approved version cannot be edited', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'FORMULATION_LOCKED', $response['raw']);

$response = call('POST', '/api/formulations/' . $formulationId . '/versions/' . $versionOneId . '/approve', null, $token);
check('an approved version cannot be approved twice', $response['status'] === 409, $response['raw']);

$response = call('POST', '/api/formulations/' . $formulationId . '/versions', null, $token);
$versionTwoId = (int) ($response['json']['data']['id'] ?? 0);
check('a second version copies the approved content', $response['status'] === 201
    && (int) ($response['json']['data']['version'] ?? 0) === 2
    && (int) ($response['json']['data']['itemCount'] ?? 0) === 2, $response['raw']);

$response = call('PUT', '/api/formulations/' . $formulationId . '/versions/' . $versionTwoId, [
    'items' => [
        ['materialId' => $materialA, 'quantity' => 55, 'unit' => 'KG', 'phase' => 'A'],
        ['materialId' => $materialB, 'quantity' => 45, 'unit' => 'KG', 'phase' => 'B'],
    ],
], $token);
check('the draft copy can be edited', $response['status'] === 200, $response['raw']);

$response = call('GET', '/api/formulations/' . $formulationId . '/compare?left=' . $versionOneId . '&right=' . $versionTwoId,
    null, $token);
$statuses = array_column($response['json']['data']['items'] ?? [], 'status');
check('the versions compare material by material', $response['status'] === 200
    && count($statuses) === 2 && !in_array('UNCHANGED', $statuses, true), $response['raw']);
check('the comparison reports the cost delta', isset($response['json']['data']['costDelta']));

$response = call('GET', '/api/formulations/' . $formulationId . '/versions', null, $token);
check('both versions are listed, newest first', count($response['json']['data'] ?? []) === 2
    && (int) ($response['json']['data'][0]['version'] ?? 0) === 2);

$response = call('POST', '/api/formulations/' . $formulationId . '/archive', null, $token);
check('a formulation is archived without losing its versions', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'ARCHIVED', $response['raw']);

$response = call('GET', '/api/formulations/999999', null, $token);
check('an unknown formulation answers 404', $response['status'] === 404);

// ------------------------------------------------------------ inventory & purchasing (phase 4)
$response = call('GET', '/api/inventory/warehouses', null, $token);
$warehouses = $response['json']['data'] ?? [];
check('the warehouses are seeded', $response['status'] === 200 && count($warehouses) === 3,
    'count ' . count($warehouses));
$materialWarehouseId = 0;
$finishedWarehouseId = 0;
$quarantineWarehouseId = 0;
foreach ($warehouses as $warehouse) {
    if (($warehouse['quarantine'] ?? false) === true) {
        $quarantineWarehouseId = (int) $warehouse['id'];
    } elseif (($warehouse['code'] ?? '') === 'MP') {
        $materialWarehouseId = (int) $warehouse['id'];
    } elseif (($warehouse['code'] ?? '') === 'PF') {
        $finishedWarehouseId = (int) $warehouse['id'];
    }
}
check('a quarantine warehouse exists', $quarantineWarehouseId > 0);
check('the raw material and finished goods stores exist', $materialWarehouseId > 0 && $finishedWarehouseId > 0);

$response = call('GET', '/api/inventory/summary', null, $token);
$summary = $response['json']['data'] ?? [];
check('the inventory summary is computed', $response['status'] === 200
    && isset($summary['stockValue'], $summary['batchCount'], $summary['lowStockItems']), $response['raw']);
check('an empty warehouse reports zero stock, not demo figures',
    (float) ($summary['stockValue'] ?? -1) === 0.0 && (int) ($summary['batchCount'] ?? -1) === 0, $response['raw']);

$response = call('GET', '/api/inventory/movements?pageSize=5', null, $token);
check('the ledger starts empty', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? -1) === 0, $response['raw']);

$response = call('GET', '/api/suppliers?pageSize=10', null, $token);
$suppliers = $response['json']['data'] ?? [];
check('the suppliers are listed', $response['status'] === 200 && count($suppliers) >= 3, $response['raw']);
$supplierId = (int) ($suppliers[0]['id'] ?? 0);
check('a supplier exposes its purchasing history', isset($suppliers[0]['orderCount'], $suppliers[0]['materialCount']));
$response = call('GET', '/api/suppliers/' . $supplierId, null, $token);
check('a supplier is read with the materials it sells', $response['status'] === 200
    && is_array($response['json']['data']['materials'] ?? null)
    && is_array($response['json']['data']['recentOrders'] ?? null), $response['raw']);
$response = call('GET', '/api/suppliers/999999', null, $token);
check('an unknown supplier answers 404', $response['status'] === 404);

$response = call('POST', '/api/purchaseOrders', ['supplierId' => $supplierId, 'items' => []], $token);
check('an order without lines is refused', $response['status'] === 422, $response['raw']);
$response = call('POST', '/api/purchaseOrders', ['supplierId' => 999999,
    'items' => [['materialId' => $materialA, 'quantity' => 1, 'unitPrice' => 10]]], $token);
check('an order for an unknown supplier answers 404', $response['status'] === 404, $response['raw']);

$response = call('POST', '/api/purchaseOrders', [
    'supplierId' => $supplierId,
    'expectedDate' => (new DateTimeImmutable('+7 days'))->format('Y-m-d'),
    'notes' => 'Commande de test phase 4',
    'items' => [
        ['materialId' => $materialA, 'quantity' => 100, 'unitPrice' => 150, 'vatRate' => 19],
        ['materialId' => $materialB, 'quantity' => 50, 'unitPrice' => 200, 'vatRate' => 19],
    ],
], $token);
$order = $response['json']['data'] ?? [];
$orderId = (int) ($order['id'] ?? 0);
$orderItemOne = (int) ($order['items'][0]['id'] ?? 0);
$orderItemTwo = (int) ($order['items'][1]['id'] ?? 0);
check('a purchase order is created as a draft', $response['status'] === 201
    && ($order['status'] ?? '') === 'DRAFT' && $orderId > 0 && $orderItemOne > 0, $response['raw']);
check('the order number is generated', str_starts_with((string) ($order['orderNumber'] ?? ''), 'BC-'));
check('the totals are computed by the server', (float) ($order['amountHt'] ?? 0) === 25000.0
    && (float) ($order['vatAmount'] ?? 0) === 4750.0 && (float) ($order['totalTtc'] ?? 0) === 29750.0,
    $response['raw']);
check('the order is denominated in Algerian dinars', ($order['currency'] ?? '') === 'DZD');

$response = call('POST', '/api/purchaseOrders/' . $orderId . '/receipts', [
    'warehouseId' => $materialWarehouseId,
    'items' => [['orderItemId' => $orderItemOne, 'quantity' => 10, 'batchNumber' => 'BT-DRAFT']],
], $token);
check('a draft order cannot be received', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PO_INVALID_STATE', $response['raw']);

$response = call('POST', '/api/purchaseOrders/' . $orderId . '/send', null, $token);
check('the order is sent to the supplier', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'SENT', $response['raw']);

$response = call('POST', '/api/purchaseOrders/' . $orderId . '/receipts', [
    'warehouseId' => $materialWarehouseId,
    'items' => [['orderItemId' => $orderItemOne, 'quantity' => 500, 'batchNumber' => 'BT-OVER']],
], $token);
check('a reception above what remains is refused', $response['status'] === 422
    && ($response['json']['error']['details']['items.0.quantity'] ?? '') === 'inventory.error.over_receipt',
    $response['raw']);

$response = call('POST', '/api/purchaseOrders/' . $orderId . '/receipts', [
    'warehouseId' => $quarantineWarehouseId,
    'release' => true,
    'items' => [['orderItemId' => $orderItemOne, 'quantity' => 10, 'batchNumber' => 'BT-QUAR']],
], $token);
check('released goods cannot be received into quarantine', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'QUARANTINE_RECEPTION', $response['raw']);

$response = call('POST', '/api/purchaseOrders/' . $orderId . '/receipts', [
    'warehouseId' => 999999,
    'items' => [['orderItemId' => $orderItemOne, 'quantity' => 10, 'batchNumber' => 'BT-NOWH']],
], $token);
check('a reception into an unknown warehouse answers 404', $response['status'] === 404, $response['raw']);

$expiry = (new DateTimeImmutable('+8 months'))->format('Y-m-d');
$response = call('POST', '/api/purchaseOrders/' . $orderId . '/receipts', [
    'warehouseId' => $materialWarehouseId,
    'items' => [['orderItemId' => $orderItemOne, 'quantity' => 40, 'batchNumber' => 'BT-TEST-1',
        'expiryDate' => $expiry, 'productionDate' => (new DateTimeImmutable('today'))->format('Y-m-d')]],
], $token);
$receipt = $response['json']['data'] ?? [];
check('a partial reception creates its receipt', $response['status'] === 201
    && str_starts_with((string) ($receipt['receiptNumber'] ?? ''), 'BR-'), $response['raw']);
check('received goods wait in quarantine', ($receipt['items'][0]['qualityStatus'] ?? '') === 'QUARANTINE');
check('the received batch holds exactly the received quantity',
    (float) ($receipt['items'][0]['batchQuantity'] ?? 0) === 40.0, $response['raw']);
check('the order becomes PARTIAL', ($receipt['orderStatus'] ?? '') === 'PARTIAL', $response['raw']);

$response = call('GET', '/api/inventory/stock?search=BT-TEST-1', null, $token);
check('the received batch appears in stock', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) === 1, $response['raw']);
$stockRow = $response['json']['data'][0] ?? [];
check('the stock line carries the warehouse, the quantity and the expiry',
    ($stockRow['warehouseName'] ?? '') !== '' && (float) ($stockRow['quantity'] ?? 0) === 40.0
    && ($stockRow['expiryDate'] ?? '') === $expiry, $response['raw']);
check('the stock line says the batch is quarantined',
    ($stockRow['qualityStatus'] ?? '') === 'QUARANTINE' && ($stockRow['daysToExpiry'] ?? 0) > 200);
$batchId = (int) ($stockRow['batchId'] ?? 0);

$response = call('GET', '/api/inventory/stock?kind=PRODUCT', null, $token);
check('finished goods stock is empty at this stage', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? -1) === 0, $response['raw']);
$response = call('GET', '/api/inventory/stock?warehouseId=' . $quarantineWarehouseId, null, $token);
check('the quarantine warehouse is empty', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? -1) === 0, $response['raw']);

$response = call('GET', '/api/inventory/fefo?kind=MATERIAL&itemId=' . $materialA . '&quantity=10', null, $token);
check('FEFO refuses to allocate quarantined stock', $response['status'] === 200
    && ($response['json']['data']['sufficient'] ?? true) === false
    && (float) ($response['json']['data']['allocated'] ?? -1) === 0.0, $response['raw']);

$response = call('POST', '/api/inventory/adjust', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'countedQuantity' => 40, 'reason' => 'Comptage identique',
], $token);
check('an adjustment without a difference is refused', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'NO_ADJUSTMENT', $response['raw']);
$response = call('POST', '/api/inventory/adjust', ['itemKind' => 'MATERIAL', 'batchId' => $batchId], $token);
check('an adjustment without a reason is refused', $response['status'] === 422, $response['raw']);
$response = call('POST', '/api/inventory/adjust', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'countedQuantity' => 45, 'reason' => 'Ecart constate au comptage',
], $token);
check('a physical count writes the difference', $response['status'] === 200
    && (float) ($response['json']['data']['delta'] ?? 0) === 5.0
    && (float) ($response['json']['data']['previousQuantity'] ?? 0) === 40.0, $response['raw']);

$response = call('POST', '/api/inventory/transfer', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'targetWarehouseId' => 999999,
    'quantity' => 1, 'reason' => 'test',
], $token);
check('a transfer to an unknown warehouse answers 404', $response['status'] === 404, $response['raw']);
$response = call('POST', '/api/inventory/transfer', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'targetWarehouseId' => $materialWarehouseId,
    'quantity' => 1, 'reason' => 'test',
], $token);
check('a transfer to the same warehouse is refused', $response['status'] === 422, $response['raw']);
$response = call('POST', '/api/inventory/transfer', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'targetWarehouseId' => $finishedWarehouseId,
    'quantity' => 1, 'reason' => 'test',
], $token);
check('a quarantined batch cannot enter a selling warehouse', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'QUARANTINE_RULE', $response['raw']);
$response = call('POST', '/api/inventory/transfer', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'targetWarehouseId' => $finishedWarehouseId,
    'quantity' => 500, 'reason' => 'test',
], $token);
check('a transfer cannot exceed the batch', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'INSUFFICIENT_STOCK', $response['raw']);

$response = call('POST', '/api/purchaseOrders/' . $orderId . '/receipts', [
    'warehouseId' => $materialWarehouseId,
    'release' => true,
    'items' => [
        ['orderItemId' => $orderItemOne, 'quantity' => 60, 'batchNumber' => 'BT-TEST-2',
            'expiryDate' => (new DateTimeImmutable('+2 months'))->format('Y-m-d')],
        ['orderItemId' => $orderItemTwo, 'quantity' => 50, 'batchNumber' => 'BT-TEST-3',
            'expiryDate' => (new DateTimeImmutable('+3 months'))->format('Y-m-d')],
    ],
], $token);
$secondReceipt = $response['json']['data'] ?? [];
check('the rest of the order is received', $response['status'] === 201
    && ($secondReceipt['orderStatus'] ?? '') === 'RECEIVED', $response['raw']);
check('a released reception is stored as RELEASED',
    in_array('RELEASED', array_column($secondReceipt['items'] ?? [], 'qualityStatus'), true), $response['raw']);

$response = call('GET', '/api/purchaseOrders/' . $orderId, null, $token);
$reloaded = $response['json']['data'] ?? [];
check('the order shows what was received line by line', $response['status'] === 200
    && (float) ($reloaded['items'][0]['receivedQty'] ?? 0) === 100.0
    && (float) ($reloaded['items'][0]['remainingQty'] ?? -1) === 0.0, $response['raw']);
check('the order lists its two receptions', count($reloaded['receipts'] ?? []) === 2, $response['raw']);

$response = call('GET', '/api/inventory/fefo?kind=MATERIAL&itemId=' . $materialA . '&quantity=100', null, $token);
$fefo = $response['json']['data'] ?? [];
check('FEFO serves the released batch first, earliest expiry first',
    ($fefo['batches'][0]['batchNumber'] ?? '') === 'BT-TEST-2', $response['raw']);
check('FEFO reports what it can serve and what is missing', (float) ($fefo['allocated'] ?? 0) === 60.0
    && (float) ($fefo['shortfall'] ?? 0) === 40.0 && ($fefo['sufficient'] ?? true) === false, $response['raw']);

$response = call('GET', '/api/inventory/stock?search=BT-TEST-2', null, $token);
$releasedBatchId = (int) ($response['json']['data'][0]['batchId'] ?? 0);
$response = call('POST', '/api/inventory/transfer', [
    'itemKind' => 'MATERIAL', 'batchId' => $releasedBatchId, 'targetWarehouseId' => $finishedWarehouseId,
    'quantity' => 20, 'reason' => 'Preparation atelier',
], $token);
check('a released batch transfers to another warehouse', $response['status'] === 200
    && (float) ($response['json']['data']['remainingSourceQuantity'] ?? -1) === 40.0, $response['raw']);
$response = call('POST', '/api/inventory/transfer', [
    'itemKind' => 'MATERIAL', 'batchId' => $releasedBatchId, 'targetWarehouseId' => $quarantineWarehouseId,
    'quantity' => 1, 'reason' => 'test',
], $token);
check('a released batch cannot enter quarantine', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'QUARANTINE_RULE', $response['raw']);

$response = call('GET', '/api/inventory/stock?search=BT-TEST-2&warehouseId=' . $finishedWarehouseId, null, $token);
$transferred = $response['json']['data'][0] ?? [];
check('the transfer created the batch in the destination warehouse', $response['status'] === 200
    && (float) ($transferred['quantity'] ?? 0) === 20.0, $response['raw']);

$response = call('GET', '/api/inventory/movements?movementType=ADJUSTMENT&pageSize=5', null, $token);
check('the ledger records the adjustment', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) === 1, $response['raw']);
$movement = $response['json']['data'][0] ?? [];
check('an adjustment names its reason and its author', ($movement['reason'] ?? '') !== ''
    && ($movement['userName'] ?? '') !== '', $response['raw']);
$response = call('GET', '/api/inventory/movements?movementType=TRANSFER&pageSize=5', null, $token);
$transferRow = $response['json']['data'][0] ?? [];
check('the ledger records the transfer with both warehouses', $response['status'] === 200
    && ($transferRow['targetWarehouseName'] ?? '') !== '' && (float) ($transferRow['quantity'] ?? 0) === 20.0,
    $response['raw']);
$response = call('GET', '/api/inventory/movements?movementType=IN&pageSize=10', null, $token);
check('the ledger records every reception', (int) ($response['json']['meta']['total'] ?? 0) === 3, $response['raw']);
$response = call('GET', '/api/inventory/movements?kind=MATERIAL&batchId=' . $releasedBatchId . '&pageSize=5', null, $token);
check('the ledger is filtered by batch', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) === 2, $response['raw']);

$response = call('GET', '/api/inventory/summary', null, $token);
$summary = $response['json']['data'] ?? [];
check('the stock value is computed from the batches', (float) ($summary['stockValue'] ?? 0) > 0, $response['raw']);
check('the summary counts the quarantined batches', (int) ($summary['quarantineBatches'] ?? 0) === 1, $response['raw']);
check('the summary counts the batches and the warehouses',
    (int) ($summary['batchCount'] ?? 0) === 4 && (int) ($summary['warehouseCount'] ?? 0) === 3, $response['raw']);

$response = call('POST', '/api/purchaseOrders', [
    'supplierId' => $supplierId,
    'items' => [['materialId' => $materialB, 'quantity' => 5, 'unitPrice' => 90]],
], $token);
$draftOrderId = (int) ($response['json']['data']['id'] ?? 0);
check('a second order is created', $response['status'] === 201, $response['raw']);
$response = call('POST', '/api/purchaseOrders/' . $draftOrderId . '/cancel', ['reason' => 'Doublon'], $token);
check('a draft order is cancelled', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'CANCELLED', $response['raw']);
$response = call('POST', '/api/purchaseOrders/' . $draftOrderId . '/send', null, $token);
check('a cancelled order cannot be sent', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PO_INVALID_STATE', $response['raw']);
$response = call('POST', '/api/purchaseOrders/' . $draftOrderId . '/receipts', [
    'warehouseId' => $materialWarehouseId,
    'items' => [['orderItemId' => 1, 'quantity' => 1, 'batchNumber' => 'BT-CANCELLED']],
], $token);
check('a cancelled order cannot be received', $response['status'] === 409, $response['raw']);
$response = call('POST', '/api/purchaseOrders/' . $orderId . '/cancel', null, $token);
check('a received order cannot be cancelled', $response['status'] === 409, $response['raw']);

$response = call('GET', '/api/purchaseOrders?pageSize=10', null, $token);
$orders = $response['json']['data'] ?? [];
check('the orders are listed with their supplier and progress', $response['status'] === 200
    && isset($orders[0]['supplierName'], $orders[0]['receivedQuantity']), $response['raw']);
$response = call('GET', '/api/purchaseOrders?status=RECEIVED', null, $token);
check('the orders are filtered by status', (int) ($response['json']['meta']['total'] ?? -1) === 1, $response['raw']);
$response = call('GET', '/api/purchaseOrders?supplierId=' . $supplierId . '&search=BC-', null, $token);
check('the orders are searched by number', (int) ($response['json']['meta']['total'] ?? -1) === 2, $response['raw']);
$response = call('GET', '/api/purchaseOrders/999999', null, $token);
check('an unknown order answers 404', $response['status'] === 404);

$response = call('GET', '/api/inventory/stock?kind=MATERIAL&availableOnly=1&pageSize=5', null, $token);
check('the stock list is filterable and paged', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) === 4, $response['raw']);
$response = call('GET', '/api/inventory/stock?expiringDays=30&pageSize=5', null, $token);
check('nothing expires within the month', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? -1) === 0, $response['raw']);
$response = call('GET', '/api/inventory/stock?expiringDays=120&pageSize=5', null, $token);
check('the stock list finds what expires soon', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) >= 2, $response['raw']);

$response = call('POST', '/api/inventory/adjust', [
    'itemKind' => 'MATERIAL', 'batchId' => $batchId, 'countedQuantity' => 1, 'reason' => 'test',
], null);
check('an anonymous adjustment is refused', $response['status'] === 401, $response['raw']);
$response = call('GET', '/api/purchaseOrders', null, null);
check('the purchasing list needs a session', $response['status'] === 401, $response['raw']);
$response = call('GET', '/api/inventory/stock?from=2026-13-01', null, $token);
check('a malformed stock filter does not break the list', $response['status'] === 200, $response['raw']);

// ------------------------------------------------------------ production (phase 5)
$response = call('GET', '/api/products?pageSize=1', null, $token);
$phase5ProductId = (int) ($response['json']['data'][0]['id'] ?? 0);
check('a finished product exists for the production run', $phase5ProductId > 0, $response['raw']);

$response = call('POST', '/api/formulations', [
    'code' => 'F-PH5', 'nameFr' => 'Emulsion phase 5', 'nameAr' => 'مستحلب المرحلة 5',
    'productId' => $phase5ProductId,
], $token);
$prodFormulationId = (int) ($response['json']['data']['id'] ?? 0);
check('a formulation is created for the production run', $response['status'] === 201 && $prodFormulationId > 0,
    $response['raw']);

$response = call('POST', '/api/formulations/' . $prodFormulationId . '/versions', [
    'instructions' => 'Phase A puis phase B, agitation 30 min.',
    'expectedYield' => 100, 'yieldUnit' => 'L',
], $token);
$prodVersionId = (int) ($response['json']['data']['id'] ?? 0);
check('its version carries the expected yield', $response['status'] === 201
    && (float) ($response['json']['data']['expectedYield'] ?? 0) === 100.0, $response['raw']);
$response = call('PUT', '/api/formulations/' . $prodFormulationId . '/versions/' . $prodVersionId, [
    'items' => [
        ['materialId' => $materialA, 'quantity' => 60, 'unit' => 'KG', 'phase' => 'A'],
        ['materialId' => $materialB, 'quantity' => 40, 'unit' => 'KG', 'phase' => 'B'],
    ],
], $token);
check('its bill of materials is written by hand', $response['status'] === 200, $response['raw']);
$response = call('POST', '/api/formulations/' . $prodFormulationId . '/versions/' . $prodVersionId . '/approve',
    null, $token);
check('the version is approved before production', $response['status'] === 200, $response['raw']);
$response = call('POST', '/api/formulations/' . $prodFormulationId . '/versions', null, $token);
$prodDraftVersionId = (int) ($response['json']['data']['id'] ?? 0);
check('a draft version stays available for the refusal test', $prodDraftVersionId > 0, $response['raw']);

$response = call('POST', '/api/purchaseOrders', [
    'supplierId' => $supplierId,
    'items' => [
        ['materialId' => $materialA, 'quantity' => 100, 'unitPrice' => 150, 'vatRate' => 19],
        ['materialId' => $materialB, 'quantity' => 100, 'unitPrice' => 200, 'vatRate' => 19],
    ],
], $token);
$prodOrderId = (int) ($response['json']['data']['id'] ?? 0);
$prodItemOne = (int) ($response['json']['data']['items'][0]['id'] ?? 0);
$prodItemTwo = (int) ($response['json']['data']['items'][1]['id'] ?? 0);
call('POST', '/api/purchaseOrders/' . $prodOrderId . '/send', null, $token);
$response = call('POST', '/api/purchaseOrders/' . $prodOrderId . '/receipts', [
    'warehouseId' => $materialWarehouseId,
    'release' => true,
    'items' => [
        ['orderItemId' => $prodItemOne, 'quantity' => 100, 'batchNumber' => 'BT-PH5-A',
            'expiryDate' => (new DateTimeImmutable('+6 months'))->format('Y-m-d')],
        ['orderItemId' => $prodItemTwo, 'quantity' => 100, 'batchNumber' => 'BT-PH5-B',
            'expiryDate' => (new DateTimeImmutable('+5 months'))->format('Y-m-d')],
    ],
], $token);
check('released raw material is available for the run', $response['status'] === 201, $response['raw']);

$response = call('GET', '/api/production/versions', null, $token);
$offeredVersions = array_column($response['json']['data'] ?? [], 'versionId');
check('the shop floor is offered the approved versions only', $response['status'] === 200
    && in_array($prodVersionId, $offeredVersions, true)
    && !in_array($prodDraftVersionId, $offeredVersions, true), $response['raw']);
$response = call('GET', '/api/production/versions', null, null);
check('the version list needs a session', $response['status'] === 401, $response['raw']);

$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodDraftVersionId, 'warehouseId' => $materialWarehouseId,
    'plannedQuantity' => 100,
], $token);
check('an unapproved version cannot be produced', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PRODUCTION_VERSION_NOT_APPROVED', $response['raw']);
$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodVersionId, 'warehouseId' => 999999, 'plannedQuantity' => 100,
], $token);
check('an unknown warehouse answers 404', $response['status'] === 404, $response['raw']);
$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodVersionId, 'warehouseId' => $materialWarehouseId, 'plannedQuantity' => 0,
], $token);
check('an order without a quantity is refused', $response['status'] === 422, $response['raw']);
$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodVersionId, 'warehouseId' => $quarantineWarehouseId, 'plannedQuantity' => 100,
], $token);
check('an order cannot consume from quarantine', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PRODUCTION_QUARANTINE', $response['raw']);

$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodVersionId, 'warehouseId' => $materialWarehouseId,
    'plannedQuantity' => 100, 'notes' => 'Lot de validation phase 5',
], $token);
$production = $response['json']['data'] ?? [];
$productionId = (int) ($production['id'] ?? 0);
check('a manufacturing order is created from the approved version', $response['status'] === 201
    && ($production['status'] ?? '') === 'DRAFT' && $productionId > 0, $response['raw']);
check('the order number is generated', str_starts_with((string) ($production['orderNumber'] ?? ''), 'PRD-'));
check('the bill of materials is scaled to the ordered quantity',
    count($production['plan'] ?? []) === 2
    && (float) ($production['plan'][0]['plannedQuantity'] ?? 0) === 60.0
    && (float) ($production['plan'][1]['plannedQuantity'] ?? 0) === 40.0, $response['raw']);
check('nothing is consumed and no batch exists yet',
    ($production['materials'] ?? []) === [] && ($production['outputs'] ?? []) === [], $response['raw']);

$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodVersionId, 'warehouseId' => $materialWarehouseId, 'plannedQuantity' => 10000,
], $token);
$starvedId = (int) ($response['json']['data']['id'] ?? 0);
check('a too large order cannot be started', $response['status'] === 201 && $starvedId > 0, $response['raw']);
$response = call('POST', '/api/production/orders/' . $starvedId . '/start', null, $token);
check('starting without stock reports what is missing', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PRODUCTION_MATERIALS'
    && count($response['json']['error']['details']['shortfalls'] ?? []) === 2, $response['raw']);
$response = call('GET', '/api/production/orders/' . $starvedId, null, $token);
check('the starved order waits for materials', ($response['json']['data']['status'] ?? '') === 'WAITING_MATERIALS',
    $response['raw']);

$response = call('POST', '/api/production/orders/' . $productionId . '/start', null, $token);
$started = $response['json']['data'] ?? [];
check('the order starts and reserves its material', $response['status'] === 200
    && ($started['status'] ?? '') === 'IN_PROGRESS', $response['raw']);
$plan = $production['plan'] ?? [];
// The warehouse already holds released batches, so the reservation is read as a FEFO plan rather
// than as a fixed list: per material, the earliest expiry goes first and the quantities add up to
// the scaled bill of materials.
$reserved = $started['materials'] ?? [];
$reservedByMaterial = [];
foreach ($reserved as $line) {
    $reservedByMaterial[(string) $line['materialCode']][] = $line;
}
$fefoHolds = $reserved !== [];
foreach ($reservedByMaterial as $lines) {
    $expiries = array_map(static fn (array $line): string => (string) ($line['expiryDate'] ?? '9999-12-31'),
        $lines);
    $sorted = $expiries;
    sort($sorted);
    $fefoHolds = $fefoHolds && $expiries === $sorted;
}
$plannedPerMaterial = [];
foreach ($plan as $plannedLine) {
    $plannedPerMaterial[(string) $plannedLine['materialCode']] = $plannedLine['plannedQuantity'];
}
foreach ($reservedByMaterial as $code => $lines) {
    $total = 0.0;
    foreach ($lines as $line) {
        $total += (float) $line['plannedQuantity'];
    }
    $fefoHolds = $fefoHolds && abs($total - (float) ($plannedPerMaterial[$code] ?? -1)) < 0.001;
}
check('the reservation is FEFO, batch by batch', $fefoHolds && count($reserved) >= 2, $response['raw']);

$response = call('POST', '/api/production/orders/' . $productionId . '/start', null, $token);
check('an order cannot be started twice', $response['status'] === 409, $response['raw']);

$reservedStock = [];
foreach ($reserved as $line) {
    $reservedStock[(string) $line['batchNumber']] = (float) $line['plannedQuantity'];
}
$consumeAll = [];
foreach ($reserved as $line) {
    $consumeAll[] = ['materialBatchId' => (int) $line['materialBatchId'], 'quantity' => (float) $line['plannedQuantity']];
}
$response = call('POST', '/api/production/orders/' . $productionId . '/consume', [
    'items' => [['materialBatchId' => (int) ($reserved[0]['materialBatchId'] ?? 0), 'quantity' => 100000]],
], $token);
check('consuming more than the batch holds is refused', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PRODUCTION_STOCK', $response['raw']);
$response = call('POST', '/api/production/orders/' . $productionId . '/complete', ['actualQuantity' => 90], $token);
check('an order without consumption cannot be completed', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PRODUCTION_NO_CONSUMPTION', $response['raw']);

$response = call('POST', '/api/production/orders/' . $productionId . '/consume', ['items' => $consumeAll], $token);
$consumed = $response['json']['data'] ?? [];
$consumedPerMaterial = [];
$expectedCost = 0.0;
foreach ($consumed['materials'] ?? [] as $line) {
    $consumedPerMaterial[(string) $line['materialCode']] = ($consumedPerMaterial[(string) $line['materialCode']] ?? 0)
        + (float) $line['consumedQuantity'];
    $expectedCost += (float) $line['consumedQuantity'] * (float) $line['unitPrice'];
}
check('the operator records what was really used', $response['status'] === 200
    && (float) ($consumed['consumedQuantity'] ?? 0) === 100.0
    && abs(($consumedPerMaterial[(string) ($plan[0]['materialCode'] ?? '')] ?? 0) - 60.0) < 0.001
    && abs(($consumedPerMaterial[(string) ($plan[1]['materialCode'] ?? '')] ?? 0) - 40.0) < 0.001,
    $response['raw']);
check('the material cost is the real cost of the consumed batches',
    (float) ($consumed['materialCost'] ?? 0) > 0
    && abs((float) ($consumed['materialCost'] ?? 0) - round($expectedCost, 2)) < 0.011, $response['raw']);
$response = call('GET', '/api/inventory/stock?search=BT-PH5-A', null, $token);
$stockA = $response['json']['data'][0] ?? [];
check('the consumed material left the stock through the ledger',
    abs((float) ($stockA['quantity'] ?? -1) - (100.0 - (float) ($reservedStock['BT-PH5-A'] ?? 0))) < 0.001,
    $response['raw']);
$response = call('GET', '/api/inventory/movements?movementType=PRODUCTION_OUT&pageSize=20', null, $token);
check('one PRODUCTION_OUT movement per consumed batch was written',
    (int) ($response['json']['meta']['total'] ?? 0) === count($reserved), $response['raw']);

$response = call('POST', '/api/production/orders/' . $productionId . '/complete', [
    'actualQuantity' => 96, 'wasteQuantity' => 2, 'packagingCost' => 300, 'otherCost' => 120,
    'expiryDate' => (new DateTimeImmutable('+18 months'))->format('Y-m-d'),
], $token);
$completed = $response['json']['data'] ?? [];
check('completing the order puts it in quality control', $response['status'] === 200
    && ($completed['status'] ?? '') === 'QUALITY_CONTROL', $response['raw']);
check('a finished batch is created in quarantine', ($completed['outputs'][0]['qualityStatus'] ?? '') === 'QUARANTINE'
    && (float) ($completed['outputs'][0]['quantity'] ?? 0) === 96.0, $response['raw']);
check('the real cost is computed per unit', (float) ($completed['totalCost'] ?? 0)
    === round((float) ($completed['materialCost'] ?? 0) + 420, 2)
    && (float) ($completed['costPerUnit'] ?? 0) > 0, $response['raw']);
check('the cost is compared with the standard', (float) ($completed['standardMaterialCost'] ?? 0) > 0
    && isset($completed['variance'], $completed['variancePct']), $response['raw']);
check('a quarantined batch is not sellable', ($completed['sellable'] ?? true) === false, $response['raw']);
$response = call('POST', '/api/production/orders/' . $productionId . '/complete', ['actualQuantity' => 10], $token);
check('an order cannot be completed twice', $response['status'] === 409, $response['raw']);
$response = call('POST', '/api/production/orders/' . $productionId . '/quality',
    ['checkType' => 'aspect', 'result' => 'MAYBE'], $token);
check('an unknown quality result is refused', $response['status'] === 422, $response['raw']);

// A second, smaller run whose quality check fails: the batch must never become sellable.
$response = call('POST', '/api/production/orders', [
    'formulationVersionId' => $prodVersionId, 'warehouseId' => $materialWarehouseId, 'plannedQuantity' => 20,
], $token);
$rejectedRunId = (int) ($response['json']['data']['id'] ?? 0);
$response = call('POST', '/api/production/orders/' . $rejectedRunId . '/start', null, $token);
$rejectedLines = $response['json']['data']['materials'] ?? [];
$rejectedItems = [];
foreach ($rejectedLines as $line) {
    $rejectedItems[] = ['materialBatchId' => (int) $line['materialBatchId'],
        'quantity' => (float) $line['plannedQuantity']];
}
$response = call('POST', '/api/production/orders/' . $rejectedRunId . '/consume', ['items' => $rejectedItems],
    $token);
check('the second run consumes its own reservation', $response['status'] === 200
    && (float) ($response['json']['data']['consumedQuantity'] ?? 0) === 20.0, $response['raw']);
$response = call('POST', '/api/production/orders/' . $rejectedRunId . '/complete',
    ['actualQuantity' => 18, 'wasteQuantity' => 2], $token);
check('the second run is completed too', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'QUALITY_CONTROL', $response['raw']);
$response = call('POST', '/api/production/orders/' . $rejectedRunId . '/quality', [
    'checkType' => 'pH', 'result' => 'FAIL', 'remarks' => 'pH hors tolérance', 'parameters' => ['ph' => 4.2],
], $token);
$rejected = $response['json']['data'] ?? [];
check('a failed check rejects the order and its batch', $response['status'] === 200
    && ($rejected['status'] ?? '') === 'REJECTED'
    && ($rejected['outputs'][0]['qualityStatus'] ?? '') === 'REJECTED', $response['raw']);

$response = call('GET', '/api/inventory/stock?kind=PRODUCT&pageSize=10', null, $token);
$productBatches = $response['json']['data'] ?? [];
check('both finished batches are in stock', (int) ($response['json']['meta']['total'] ?? 0) === 2,
    $response['raw']);
check('one batch waits in quarantine and one is rejected',
    array_column($productBatches, 'qualityStatus') === ['QUARANTINE', 'REJECTED']
    || array_column($productBatches, 'qualityStatus') === ['REJECTED', 'QUARANTINE'], $response['raw']);
$response = call('GET', '/api/inventory/stock?kind=PRODUCT&availableOnly=1', null, $token);
check('no unreleased finished batch is offered for sale',
    (int) ($response['json']['meta']['total'] ?? -1) === 0, $response['raw']);

$response = call('POST', '/api/production/orders/' . $productionId . '/quality', [
    'checkType' => 'aspect', 'result' => 'PASS', 'remarks' => 'Conforme',
    'parameters' => ['ph' => 5.5, 'viscosity' => 4200],
], $token);
$released = $response['json']['data'] ?? [];
check('a PASS check releases the batch and closes the order', $response['status'] === 200
    && ($released['status'] ?? '') === 'COMPLETED'
    && ($released['outputs'][0]['qualityStatus'] ?? '') === 'RELEASED'
    && ($released['sellable'] ?? false) === true, $response['raw']);
check('the quality decision is recorded with its measurements',
    count($released['qualityChecks'] ?? []) === 1
    && (float) ($released['qualityChecks'][0]['parameters']['ph'] ?? 0) === 5.5
    && ($released['qualityChecks'][0]['checkedByName'] ?? '') === 'admin', $response['raw']);
$response = call('GET', '/api/inventory/stock?kind=PRODUCT&availableOnly=1', null, $token);
check('the released batch is now sellable', (int) ($response['json']['meta']['total'] ?? 0) === 1,
    $response['raw']);

$response = call('GET', '/api/production/orders/' . $productionId . '/genealogy', null, $token);
$genealogy = $response['json']['data'] ?? [];
check('the genealogy links the output to its material batches', $response['status'] === 200
    && ($genealogy['complete'] ?? false) === true
    && count($genealogy['inputs'] ?? []) === count($reserved)
    && count($genealogy['outputs'] ?? []) === 1, $response['raw']);
check('every input names its supplier and its receipt', ($genealogy['inputs'][0]['supplierName'] ?? '') !== ''
    && ($genealogy['inputs'][0]['receiptNumber'] ?? '') !== ''
    && ($genealogy['inputs'][0]['purchaseOrderNumber'] ?? '') !== '', $response['raw']);
check('the genealogy reports the consumed quantities',
    (float) ($genealogy['inputs'][0]['consumedQuantity'] ?? 0) > 0, $response['raw']);
$response = call('GET', '/api/production/orders/999999/genealogy', null, $token);
check('the genealogy of an unknown order answers 404', $response['status'] === 404, $response['raw']);

$response = call('POST', '/api/production/orders/' . $starvedId . '/cancel', ['reason' => 'Stock indisponible'],
    $token);
check('an order waiting for materials can be cancelled', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'CANCELLED', $response['raw']);
$response = call('POST', '/api/production/orders/' . $rejectedRunId . '/cancel', null, $token);
check('an order that consumed material cannot be cancelled', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PRODUCTION_CONSUMED', $response['raw']);
$response = call('POST', '/api/production/orders/' . $productionId . '/cancel', null, $token);
check('a completed order cannot be cancelled', $response['status'] === 409, $response['raw']);

$response = call('GET', '/api/production/orders?pageSize=10', null, $token);
check('the orders are listed with their progress', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) === 3
    && isset($response['json']['data'][0]['consumedQuantity'], $response['json']['data'][0]['productName']),
    $response['raw']);
$response = call('GET', '/api/production/orders?status=COMPLETED', null, $token);
check('the orders are filtered by status', (int) ($response['json']['meta']['total'] ?? -1) === 1,
    $response['raw']);
$response = call('GET', '/api/production/orders?search=PRD-', null, $token);
check('the orders are searched by number', (int) ($response['json']['meta']['total'] ?? 0) === 3,
    $response['raw']);
$response = call('GET', '/api/production/orders/999999', null, $token);
check('an unknown order answers 404', $response['status'] === 404, $response['raw']);
$response = call('GET', '/api/production/orders', null, null);
check('the production list needs a session', $response['status'] === 401, $response['raw']);

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
check('the audit trail records actionable entries', $actions !== [], implode(',', $actions));
$response = call('GET', '/api/auditLogs?action=LOGIN&pageSize=10', null, $token);
$logins = array_column($response['json']['data'] ?? [], 'action');
check('the audit trail contains a LOGIN entry', in_array('LOGIN', $logins, true), implode(',', $logins));
$response = call('GET', '/api/auditLogs?action=CREATE&pageSize=10', null, $token);
check('the audit trail contains a CREATE entry',
    in_array('CREATE', array_column($response['json']['data'] ?? [], 'action'), true), $response['raw']);
$response = call('GET', '/api/auditLogs?action=APPROVE&pageSize=10', null, $token);
check('the approval is audited', in_array('APPROVE',
    array_column($response['json']['data'] ?? [], 'action'), true), $response['raw']);

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
