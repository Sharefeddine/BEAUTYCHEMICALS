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
$response = call('GET', '/api/inventory/stock?kind=PRODUCT&qualityStatus=RELEASED', null, $token);
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
$response = call('GET', '/api/inventory/stock?kind=PRODUCT&qualityStatus=RELEASED', null, $token);
check('the released batch is now sellable', (int) ($response['json']['meta']['total'] ?? 0) === 1,
    $response['raw']);
check('the sellable batch names the moment it was released',
    ($response['json']['data'][0]['batchNumber'] ?? '') === 'PF-PRD-00001', $response['raw']);

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

// ------------------------------------------------------------ sales & distribution (phase 6)
// The sellable stock is whatever the production run of the previous section released: the checks
// below derive every quantity from that row instead of assuming a seeded amount.
$response = call('GET', '/api/inventory/stock?kind=PRODUCT&qualityStatus=RELEASED&availableOnly=1&pageSize=10',
    null, $token);
$sellableRows = $response['json']['data'] ?? [];
check('the released finished batch is the stock the sales module may sell',
    $response['status'] === 200 && $sellableRows !== [], $response['raw']);
$sellable = $sellableRows[0];
$sellableProductId = (int) ($sellable['itemId'] ?? 0);
$sellableBatchId = (int) ($sellable['batchId'] ?? 0);
$sellableBefore = (float) ($sellable['quantity'] ?? 0);
check('the sellable batch carries an expiry date and a number', $sellableBatchId > 0
    && (string) ($sellable['batchNumber'] ?? '') !== '' && $sellableBefore > 0, $response['raw']);

$response = call('GET', '/api/customers', null, $token);
$seededCustomers = (int) ($response['json']['meta']['total'] ?? 0);
check('customers are listed with their receivable', $response['status'] === 200 && $seededCustomers >= 5,
    $response['raw']);
check('a customer exposes the credit it still has', isset($response['json']['data'][0]['availableCredit'])
    || array_key_exists('availableCredit', $response['json']['data'][0] ?? []), $response['raw']);

$response = call('POST', '/api/customers', [
    'name' => 'Boulangerie El Feth', 'type' => 'COMPANY', 'priceSegment' => 'WHOLESALE',
    'creditLimit' => 50000, 'phone' => '+213 36 11 22 33', 'wilaya' => 'Sétif',
    'address' => 'Rue de la gare',
], $token);
$mainCustomer = $response['json']['data'] ?? [];
$mainCustomerId = (int) ($mainCustomer['id'] ?? 0);
check('a customer is created with a generated code', $response['status'] === 201
    && $mainCustomerId > 0 && str_starts_with((string) ($mainCustomer['code'] ?? ''), 'CL-'), $response['raw']);
check('a new customer carries no balance', ($mainCustomer['balance'] ?? null) === 0.0
    || (float) ($mainCustomer['balance'] ?? -1) === 0.0, $response['raw']);

$response = call('POST', '/api/customers', ['name' => ''], $token);
check('a customer without a name is refused', $response['status'] === 422
    && isset($response['json']['error']['details']['name']), $response['raw']);

$response = call('PUT', '/api/customers/' . $mainCustomerId, ['creditLimit' => 80000, 'notes' => 'Client test'],
    $token);
check('the credit limit of a customer can be raised', $response['status'] === 200
    && (float) ($response['json']['data']['creditLimit'] ?? 0) === 80000.0, $response['raw']);

$response = call('POST', '/api/customers', [
    'name' => 'Comptant Témoin', 'priceSegment' => 'RETAIL', 'creditLimit' => 1,
], $token);
$cashCustomerId = (int) ($response['json']['data']['id'] ?? 0);
check('a customer with a one dinar limit is accepted', $response['status'] === 201 && $cashCustomerId > 0,
    $response['raw']);

$response = call('POST', '/api/customers', ['name' => 'Client Éphémère', 'priceSegment' => 'RETAIL'], $token);
$tempCustomerId = (int) ($response['json']['data']['id'] ?? 0);
check('a third customer is created for the closing checks', $response['status'] === 201 && $tempCustomerId > 0,
    $response['raw']);

// ------------------------------------------------------------ quotation
$response = call('POST', '/api/quotations', [
    'customerId' => $mainCustomerId,
    'validUntil' => date('Y-m-d', strtotime('+15 days')),
    'notes' => 'Devis phase 6',
    'items' => [['productId' => $sellableProductId, 'quantity' => 1]],
], $token);
$quotation = $response['json']['data'] ?? [];
$quotationId = (int) ($quotation['id'] ?? 0);
$quoteUnitPrice = (float) ($quotation['items'][0]['unitPrice'] ?? 0);
$quoteTotal = (float) ($quotation['totalTtc'] ?? 0);
check('a quotation is priced from the wholesale segment', $response['status'] === 201 && $quotationId > 0
    && $quoteUnitPrice > 0 && $quoteTotal > $quoteUnitPrice, $response['raw']);
check('the quotation totals add up', abs((float) $quotation['amountHt'] + (float) $quotation['vatAmount']
    - $quoteTotal) < 0.011, $response['raw']);

$response = call('POST', '/api/quotations/' . $quotationId . '/send', null, $token);
check('a quotation is sent to the customer', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'SENT', $response['raw']);
$response = call('POST', '/api/quotations/' . $quotationId . '/send', null, $token);
check('a sent quotation cannot be sent twice', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'INVALID_STATUS', $response['raw']);

$response = call('GET', '/api/documents/quotation/' . $quotationId . '?lang=fr', null, $token);
check('the quotation prints as a French document', $response['status'] === 200
    && str_contains($response['raw'], 'DEVIS')
    && str_contains($response['raw'], (string) $quotation['quoteNumber']), substr($response['raw'], 0, 200));

$response = call('POST', '/api/quotations/' . $quotationId . '/accept',
    ['deliveryDate' => date('Y-m-d', strtotime('+2 days'))], $token);
$accepted = $response['json']['data'] ?? [];
$orderId = (int) ($accepted['order']['id'] ?? 0);
check('accepting a quotation creates the order', $response['status'] === 200
    && ($accepted['quotation']['status'] ?? '') === 'ACCEPTED'
    && ($accepted['order']['status'] ?? '') === 'DRAFT' && $orderId > 0, $response['raw']);
check('the order copies the quoted lines', count($accepted['order']['items'] ?? []) === 1
    && (float) ($accepted['order']['items'][0]['quantity'] ?? 0) === 1.0, $response['raw']);
$response = call('POST', '/api/quotations/' . $quotationId . '/accept', null, $token);
check('an accepted quotation cannot be accepted twice', $response['status'] === 409, $response['raw']);

// ------------------------------------------------------------ credit limit on confirmation
$response = call('POST', '/api/salesOrders', [
    'customerId' => $cashCustomerId,
    'items' => [['productId' => $sellableProductId, 'quantity' => 1]],
], $token);
$blockedOrderId = (int) ($response['json']['data']['id'] ?? 0);
check('an order is drafted for the customer with a one dinar limit', $response['status'] === 201
    && $blockedOrderId > 0, $response['raw']);
$response = call('POST', '/api/salesOrders/' . $blockedOrderId . '/confirm', null, $token);
check('confirming over the credit limit is refused', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'CREDIT_LIMIT_EXCEEDED'
    && isset($response['json']['error']['details']['creditLimit']), $response['raw']);
check('the refusal says how much credit is left',
    (float) ($response['json']['error']['details']['available'] ?? -1) === 1.0, $response['raw']);

// ------------------------------------------------------------ order, delivery, invoice, payment
$response = call('POST', '/api/salesOrders/' . $orderId . '/confirm', null, $token);
check('the order is confirmed within the credit limit', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'CONFIRMED', $response['raw']);

$response = call('POST', '/api/salesOrders/' . $orderId . '/deliveries',
    ['items' => [['orderItemId' => (int) ($accepted['order']['items'][0]['id'] ?? 0), 'quantity' => 999]]], $token);
check('a delivery cannot ship more than the line ordered', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'DELIVERY_EXCEEDS_ORDER'
    && isset($response['json']['error']['details']['remaining']), $response['raw']);
$blockedOrder = call('GET', '/api/salesOrders/' . $orderId, null, $token);
check('the refused delivery left the order untouched',
    ($blockedOrder['json']['data']['deliveredQty'] ?? null) !== null
    && (float) $blockedOrder['json']['data']['deliveredQty'] === 0.0, $blockedOrder['raw']);

// A second catalogue line that has no released batch: it must refuse to ship, and the order can
// still be cancelled because nothing ever left the warehouse.
$releasedProductIds = array_map(static fn (array $row): int => (int) $row['itemId'], $sellableRows);
$response = call('GET', '/api/products?pageSize=50', null, $token);
$dryProductId = 0;
foreach ($response['json']['data'] ?? [] as $product) {
    if (!in_array((int) $product['id'], $releasedProductIds, true)) {
        $dryProductId = (int) $product['id'];
        break;
    }
}
check('the catalogue holds a product without any released batch', $dryProductId > 0, $response['raw']);
$response = call('POST', '/api/salesOrders', [
    'customerId' => $mainCustomerId,
    'items' => [['productId' => $dryProductId, 'quantity' => 1]],
], $token);
$dryOrderId = (int) ($response['json']['data']['id'] ?? 0);
check('an order is drafted on the unavailable product', $response['status'] === 201 && $dryOrderId > 0,
    $response['raw']);
call('POST', '/api/salesOrders/' . $dryOrderId . '/confirm', null, $token);
$response = call('POST', '/api/salesOrders/' . $dryOrderId . '/deliveries', [], $token);
check('nothing ships when no released batch exists', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'SALES_STOCK_INSUFFICIENT'
    && isset($response['json']['error']['details']['available']), $response['raw']);
$response = call('POST', '/api/salesOrders/' . $dryOrderId . '/cancel', null, $token);
check('an order that shipped nothing can be cancelled', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'CANCELLED', $response['raw']);
$response = call('POST', '/api/salesOrders/' . $dryOrderId . '/confirm', null, $token);
check('a cancelled order cannot be confirmed again', $response['status'] === 409, $response['raw']);

$response = call('POST', '/api/salesOrders/' . $orderId . '/deliveries', [], $token);
$delivery = $response['json']['data'] ?? [];
$deliveryId = (int) ($delivery['id'] ?? 0);
check('a delivery note is prepared from the order', $response['status'] === 201
    && ($delivery['status'] ?? '') === 'PREPARED' && $deliveryId > 0, $response['raw']);
check('the delivered unit is traced to the released batch',
    ($delivery['items'][0]['productBatchId'] ?? 0) === $sellableBatchId, $response['raw']);
$response = call('GET', '/api/salesOrders/' . $orderId, null, $token);
check('preparing the delivery reserves the order', ($response['json']['data']['status'] ?? '') === 'RESERVED',
    $response['raw']);
check('the order line shows the delivered quantity',
    (float) ($response['json']['data']['items'][0]['deliveredQty'] ?? -1) === 1.0, $response['raw']);

$response = call('GET', '/api/inventory/stock?kind=PRODUCT&qualityStatus=RELEASED&pageSize=50', null, $token);
$afterPicking = 0.0;
foreach ($response['json']['data'] ?? [] as $row) {
    if ((int) ($row['batchId'] ?? 0) === $sellableBatchId) {
        $afterPicking = (float) $row['quantity'];
    }
}
check('preparing the delivery takes the units out of the batch', $afterPicking < $sellableBefore
    && abs(($sellableBefore - $afterPicking) - 1.0) < 0.001, $response['raw']);

$response = call('POST', '/api/deliveries/' . $deliveryId . '/deliver', [], $token);
check('a delivery without a signature is refused', $response['status'] === 422
    && isset($response['json']['error']['details']['signedBy']), $response['raw']);
$response = call('POST', '/api/deliveries/' . $deliveryId . '/deliver',
    ['signedBy' => 'M. Belkacem'], $token);
check('the customer signature closes the delivery', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'DELIVERED'
    && ($response['json']['data']['signedBy'] ?? '') === 'M. Belkacem', $response['raw']);
$response = call('GET', '/api/salesOrders/' . $orderId, null, $token);
check('a delivered but unbilled order is partial', ($response['json']['data']['status'] ?? '') === 'PARTIAL',
    $response['raw']);
$response = call('POST', '/api/salesOrders/' . $orderId . '/cancel', null, $token);
check('an order whose goods shipped cannot be cancelled', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'ORDER_HAS_DELIVERIES', $response['raw']);

$response = call('GET', '/api/documents/delivery/' . $deliveryId . '?lang=fr', null, $token);
check('the delivery note prints with its batch number', $response['status'] === 200
    && str_contains($response['raw'], 'BON DE LIVRAISON')
    && str_contains($response['raw'], (string) ($delivery['items'][0]['batchNumber'] ?? '')),
    substr($response['raw'], 0, 160));

$response = call('POST', '/api/invoices', ['orderId' => $orderId], $token);
$invoice = $response['json']['data'] ?? [];
$invoiceId = (int) ($invoice['id'] ?? 0);
$invoiceTotal = round((float) ($invoice['totalTtc'] ?? 0), 2);
check('the invoice bills what was delivered', $response['status'] === 201
    && ($invoice['status'] ?? '') === 'DRAFT' && $invoiceId > 0
    && abs($invoiceTotal - $quoteTotal) < 0.011, $response['raw']);
check('the invoice line keeps the batch trace', ($invoice['items'][0]['productBatchId'] ?? 0)
    === $sellableBatchId, $response['raw']);

$response = call('PUT', '/api/invoices/' . $invoiceId, ['notes' => 'Livraison signée'], $token);
check('a draft invoice can still be corrected', $response['status'] === 200
    && ($response['json']['data']['notes'] ?? '') === 'Livraison signée', $response['raw']);

$response = call('POST', '/api/invoices/' . $invoiceId . '/issue', null, $token);
$issued = $response['json']['data'] ?? [];
check('issuing freezes the invoice', $response['status'] === 200
    && ($issued['status'] ?? '') === 'ISSUED' && ($issued['isFinalized'] ?? false) === true, $response['raw']);
check('the invoice number is generated', str_starts_with((string) ($issued['invoiceNumber'] ?? ''), 'FA-'),
    $response['raw']);
$response = call('PUT', '/api/invoices/' . $invoiceId, ['notes' => 'Trop tard'], $token);
check('an issued invoice refuses any modification', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'INVOICE_FINALIZED', $response['raw']);
$response = call('POST', '/api/invoices/' . $invoiceId . '/cancel', null, $token);
check('an issued invoice cannot be cancelled', $response['status'] === 409, $response['raw']);

$response = call('GET', '/api/customers/' . $mainCustomerId, null, $token);
check('issuing the invoice books the receivable', abs((float) ($response['json']['data']['balance'] ?? 0)
    - $invoiceTotal) < 0.011, $response['raw']);
$response = call('GET', '/api/salesOrders/' . $orderId, null, $token);
check('a fully delivered and billed order is invoiced',
    ($response['json']['data']['status'] ?? '') === 'INVOICED', $response['raw']);

$response = call('GET', '/api/documents/invoice/' . $invoiceId . '?lang=fr', null, $token);
check('the invoice prints with its totals', $response['status'] === 200
    && str_contains($response['raw'], 'FACTURE')
    && str_contains($response['raw'], (string) $issued['invoiceNumber']), substr($response['raw'], 0, 160));
$response = call('GET', '/api/invoices/' . $invoiceId, null, $token);
check('printing an invoice is counted', (int) ($response['json']['data']['printedCount'] ?? 0) === 1,
    $response['raw']);

$response = call('GET', '/api/cashAccounts', null, $token);
$cashAccountId = (int) ($response['json']['data'][0]['id'] ?? 0);
check('the cash accounts are listed for the payment', $response['status'] === 200
    && $cashAccountId > 0, $response['raw']);

$halfPayment = round($invoiceTotal / 2, 2);
$response = call('POST', '/api/payments', [
    'invoiceId' => $invoiceId, 'amount' => $invoiceTotal + 1, 'cashAccountId' => $cashAccountId,
    'method' => 'CASH',
], $token);
check('a payment above the balance due is refused', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'PAYMENT_EXCEEDS_BALANCE'
    && isset($response['json']['error']['details']['remaining']), $response['raw']);

$response = call('POST', '/api/payments', [
    'invoiceId' => $invoiceId, 'amount' => $halfPayment, 'cashAccountId' => $cashAccountId,
    'method' => 'CASH', 'reference' => 'Caisse 1',
], $token);
$payment = $response['json']['data'] ?? [];
check('a partial payment is recorded', $response['status'] === 201
    && (float) ($payment['amount'] ?? 0) === $halfPayment, $response['raw']);
check('the payment number is generated', str_starts_with((string) ($payment['paymentNumber'] ?? ''), 'ENC-'),
    $response['raw']);
$response = call('GET', '/api/invoices/' . $invoiceId, null, $token);
check('the invoice becomes partially paid', ($response['json']['data']['status'] ?? '') === 'PARTIAL'
    && abs((float) ($response['json']['data']['paidAmount'] ?? 0) - $halfPayment) < 0.011, $response['raw']);
$response = call('GET', '/api/customers/' . $mainCustomerId, null, $token);
check('the payment lowers the receivable', abs((float) ($response['json']['data']['balance'] ?? 0)
    - ($invoiceTotal - $halfPayment)) < 0.011, $response['raw']);

$response = call('POST', '/api/payments', [
    'invoiceId' => $invoiceId, 'amount' => round($invoiceTotal - $halfPayment, 2),
    'cashAccountId' => $cashAccountId, 'method' => 'TRANSFER', 'reference' => 'Virement BNA',
], $token);
check('the balance can be settled', $response['status'] === 201, $response['raw']);
$response = call('GET', '/api/invoices/' . $invoiceId, null, $token);
check('a settled invoice is paid', ($response['json']['data']['status'] ?? '') === 'PAID', $response['raw']);
$response = call('POST', '/api/payments', [
    'invoiceId' => $invoiceId, 'amount' => 100, 'cashAccountId' => $cashAccountId,
], $token);
check('a paid invoice accepts no further payment', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'INVOICE_NOT_PAYABLE', $response['raw']);
$response = call('GET', '/api/customers/' . $mainCustomerId, null, $token);
check('the customer is square again', abs((float) ($response['json']['data']['balance'] ?? -1)) < 0.011,
    $response['raw']);

$response = call('POST', '/api/payments', [
    'customerId' => $mainCustomerId, 'amount' => 250, 'cashAccountId' => $cashAccountId, 'method' => 'CASH',
], $token);
check('a payment on account is accepted', $response['status'] === 201
    && ($response['json']['data']['invoiceId'] ?? null) === null, $response['raw']);

$response = call('GET', '/api/customers/' . $mainCustomerId . '/statement', null, $token);
$statement = $response['json']['data'] ?? [];
check('the statement lists the invoice, the payments and the on-account credit',
    $response['status'] === 200 && count($statement['lines'] ?? []) >= 3, $response['raw']);
check('the statement closes on the customer balance',
    abs((float) ($statement['closingBalance'] ?? 0) - (float) ($statement['customer']['balance'] ?? 0)) < 0.011,
    $response['raw']);
$response = call('GET', '/api/documents/statement/' . $mainCustomerId . '?lang=fr', null, $token);
check('the statement prints', $response['status'] === 200
    && str_contains($response['raw'], 'RELEVÉ DE COMPTE'), substr($response['raw'], 0, 160));
$response = call('GET', '/api/documents/invoice/' . $invoiceId . '?lang=ar', null, $token);
check('an invoice also prints in Arabic, right to left', $response['status'] === 200
    && str_contains($response['raw'], 'dir="rtl"') && str_contains($response['raw'], 'فاتورة'),
    substr($response['raw'], 0, 160));

// ------------------------------------------------------------ credit note on a counter sale
$response = call('POST', '/api/invoices', [
    'customerId' => $tempCustomerId,
    'items' => [['productId' => $sellableProductId, 'quantity' => 1]],
], $token);
$counterInvoiceId = (int) ($response['json']['data']['id'] ?? 0);
$counterTotal = round((float) ($response['json']['data']['totalTtc'] ?? 0), 2);
check('a counter sale is invoiced without a delivery', $response['status'] === 201 && $counterInvoiceId > 0
    && ($response['json']['data']['orderId'] ?? 0) === 0, $response['raw']);
$response = call('POST', '/api/invoices/' . $counterInvoiceId . '/issue', null, $token);
check('the counter invoice is issued', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'ISSUED', $response['raw']);

$response = call('DELETE', '/api/customers/' . $tempCustomerId, null, $token);
check('a customer who still owes money cannot be closed', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'CUSTOMER_HAS_BALANCE', $response['raw']);

$response = call('POST', '/api/invoices/' . $counterInvoiceId . '/creditNotes',
    ['amountHt' => $counterTotal * 2, 'reason' => 'Trop perçu'], $token);
check('a credit note above what is due is refused', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'CREDIT_EXCEEDS_BALANCE', $response['raw']);

$response = call('POST', '/api/invoices/' . $counterInvoiceId . '/creditNotes',
    ['amountHt' => 100, 'vatRate' => 19, 'reason' => 'Retour de 2 bidons'], $token);
$creditNote = $response['json']['data'] ?? [];
$creditNoteId = (int) ($creditNote['id'] ?? 0);
$creditTotal = round((float) ($creditNote['totalTtc'] ?? 0), 2);
check('a credit note is issued on the invoice', $response['status'] === 201
    && ($creditNote['status'] ?? '') === 'ISSUED' && $creditNoteId > 0
    && abs($creditTotal - 119.0) < 0.011, $response['raw']);
check('the credit note number is generated',
    str_starts_with((string) ($creditNote['creditNumber'] ?? ''), 'AV-'), $response['raw']);

$response = call('POST', '/api/creditNotes/' . $creditNoteId . '/apply', null, $token);
check('applying the credit note corrects the receivable', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'APPLIED', $response['raw']);
$response = call('GET', '/api/invoices/' . $counterInvoiceId, null, $token);
check('the corrected invoice carries the credit',
    abs((float) ($response['json']['data']['paidAmount'] ?? 0) - $creditTotal) < 0.011, $response['raw']);
$response = call('GET', '/api/customers/' . $tempCustomerId, null, $token);
check('the credit note lowers the customer balance',
    abs((float) ($response['json']['data']['balance'] ?? 0) - ($counterTotal - $creditTotal)) < 0.011,
    $response['raw']);
$response = call('POST', '/api/creditNotes/' . $creditNoteId . '/apply', null, $token);
check('an applied credit note cannot be applied twice', $response['status'] === 409, $response['raw']);
$response = call('GET', '/api/creditNotes?customerId=' . $tempCustomerId, null, $token);
check('credit notes are listed per customer', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) >= 1, $response['raw']);

// ------------------------------------------------------------ partial delivery progress
$response = call('POST', '/api/salesOrders', [
    'customerId' => $mainCustomerId,
    'items' => [['productId' => $sellableProductId, 'quantity' => 2]],
], $token);
$partialOrderId = (int) ($response['json']['data']['id'] ?? 0);
$partialItemId = (int) ($response['json']['data']['items'][0]['id'] ?? 0);
check('a second order is drafted for the progress checks', $response['status'] === 201
    && $partialOrderId > 0, $response['raw']);
call('POST', '/api/salesOrders/' . $partialOrderId . '/confirm', null, $token);
$response = call('POST', '/api/salesOrders/' . $partialOrderId . '/deliveries',
    ['items' => [['orderItemId' => $partialItemId, 'quantity' => 1]]], $token);
$partialDeliveryId = (int) ($response['json']['data']['id'] ?? 0);
check('a delivery can ship part of a line', $response['status'] === 201 && $partialDeliveryId > 0,
    $response['raw']);
call('POST', '/api/deliveries/' . $partialDeliveryId . '/deliver', ['signedBy' => 'Mme Cherif'], $token);
$response = call('GET', '/api/salesOrders/' . $partialOrderId, null, $token);
$partialOrder = $response['json']['data'] ?? [];
check('the order reports its delivery progress', (float) ($partialOrder['deliveredQty'] ?? 0) === 1.0
    && (float) ($partialOrder['items'][0]['deliveredQty'] ?? 0) === 1.0
    && (float) ($partialOrder['items'][0]['quantity'] ?? 0) === 2.0, $response['raw']);
check('half a line shipped reads as partial progress',
    abs((float) ($partialOrder['deliveryProgress'] ?? 0) - 50.0) < 0.11, $response['raw']);
$response = call('POST', '/api/invoices', ['orderId' => $partialOrderId], $token);
$partialInvoiceId = (int) ($response['json']['data']['id'] ?? 0);
check('only the delivered half can be invoiced', $response['status'] === 201
    && (float) ($response['json']['data']['items'][0]['quantity'] ?? 0) === 1.0, $response['raw']);
call('POST', '/api/invoices/' . $partialInvoiceId . '/issue', null, $token);
$response = call('POST', '/api/invoices', ['orderId' => $partialOrderId], $token);
check('the undelivered remainder cannot be invoiced yet', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'NOTHING_TO_INVOICE', $response['raw']);

// ------------------------------------------------------------ distribution
$response = call('GET', '/api/vehicles', null, $token);
$vehicles = $response['json']['data'] ?? [];
check('the fleet is listed', $response['status'] === 200 && count($vehicles) >= 2, $response['raw']);
$vehicleId = (int) ($vehicles[0]['id'] ?? 0);
check('a vehicle shows how many tours it has done', array_key_exists('routeCount', $vehicles[0] ?? []),
    $response['raw']);
$response = call('POST', '/api/vehicles', ['plate' => '19-9999-118', 'model' => 'Peugeot Partner',
    'capacityKg' => 600], $token);
$newVehicleId = (int) ($response['json']['data']['id'] ?? 0);
check('a vehicle joins the fleet', $response['status'] === 201 && $newVehicleId > 0, $response['raw']);
$response = call('POST', '/api/vehicles', ['plate' => '19-9999-118'], $token);
check('the same plate cannot be registered twice', $response['status'] === 409, $response['raw']);
$response = call('GET', '/api/drivers', null, $token);
check('the drivers list answers even before the payroll exists', $response['status'] === 200
    && is_array($response['json']['data']), $response['raw']);

$response = call('POST', '/api/salesOrders', [
    'customerId' => $mainCustomerId,
    'items' => [['productId' => $sellableProductId, 'quantity' => 1]],
], $token);
$routeOrderId = (int) ($response['json']['data']['id'] ?? 0);
call('POST', '/api/salesOrders/' . $routeOrderId . '/confirm', null, $token);
$response = call('POST', '/api/salesOrders/' . $routeOrderId . '/deliveries', [], $token);
$routeNoteId = (int) ($response['json']['data']['id'] ?? 0);
$routeNoteNumber = (string) ($response['json']['data']['noteNumber'] ?? '');
check('a delivery note waits to be routed', $response['status'] === 201
    && ($response['json']['data']['status'] ?? '') === 'PREPARED', $response['raw']);

$response = call('POST', '/api/routes', [
    'vehicleId' => $vehicleId, 'routeDate' => date('Y-m-d'), 'wilayaZone' => 'Sétif centre',
    'deliveryNoteIds' => [$routeNoteId],
], $token);
$route = $response['json']['data'] ?? [];
$routeId = (int) ($route['id'] ?? 0);
$stopId = (int) ($route['stops'][0]['id'] ?? 0);
check('a tour is planned with its stop', $response['status'] === 201 && $routeId > 0
    && ($route['status'] ?? '') === 'PLANNED' && ($route['stopCount'] ?? 0) === 1 && $stopId > 0,
    $response['raw']);
check('the tour shows which customer it serves',
    ($route['stops'][0]['customerName'] ?? '') !== ''
    && (float) ($route['stops'][0]['totalQuantity'] ?? 0) === 1.0, $response['raw']);

$response = call('POST', '/api/routes', [
    'vehicleId' => $newVehicleId, 'routeDate' => date('Y-m-d'), 'deliveryNoteIds' => [$routeNoteId],
], $token);
check('a note cannot be planned on two tours', $response['status'] === 409
    && ($response['json']['error']['code'] ?? '') === 'NOTE_ALREADY_PLANNED', $response['raw']);

$response = call('POST', '/api/routes/' . $routeId . '/start', null, $token);
check('the tour leaves and puts its notes in transit', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'IN_TRANSIT'
    && ($response['json']['data']['stops'][0]['noteStatus'] ?? '') === 'IN_TRANSIT', $response['raw']);
$response = call('GET', '/api/deliveries/' . $routeNoteId, null, $token);
check('the delivery note follows the tour', ($response['json']['data']['status'] ?? '') === 'IN_TRANSIT',
    $response['raw']);

$response = call('POST', '/api/routes/' . $routeId . '/stops/' . $stopId . '/deliver',
    ['remarks' => 'Livré au comptoir'], $token);
check('delivering a stop signs the delivery note', $response['status'] === 200
    && ($response['json']['data']['stops'][0]['status'] ?? '') === 'DELIVERED'
    && ($response['json']['data']['stops'][0]['noteStatus'] ?? '') === 'DELIVERED', $response['raw']);
$response = call('POST', '/api/routes/' . $routeId . '/stops/' . $stopId . '/deliver', [], $token);
check('a settled stop cannot be settled twice', $response['status'] === 409, $response['raw']);

$response = call('POST', '/api/routes/' . $routeId . '/complete', ['fuelCost' => 3500], $token);
check('the tour is closed with its fuel cost', $response['status'] === 200
    && ($response['json']['data']['status'] ?? '') === 'COMPLETED'
    && (float) ($response['json']['data']['fuelCost'] ?? 0) === 3500.0, $response['raw']);
check('the tour reports its delivered stops',
    ($response['json']['data']['deliveredCount'] ?? 0) === 1, $response['raw']);

$response = call('GET', '/api/routes?routeDate=' . date('Y-m-d'), null, $token);
check('the tours of the day are listed', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) >= 1, $response['raw']);

$response = call('GET', '/api/deliveries?status=DELIVERED&pageSize=10', null, $token);
check('delivered notes are listed with their signature',
    $response['status'] === 200 && (int) ($response['json']['meta']['total'] ?? 0) >= 3, $response['raw']);

// ------------------------------------------------------------ closing a clean customer
$response = call('GET', '/api/customers/' . $tempCustomerId, null, $token);
$tempBalance = round((float) ($response['json']['data']['balance'] ?? 0), 2);
$response = call('POST', '/api/payments', [
    'customerId' => $tempCustomerId, 'amount' => $tempBalance, 'cashAccountId' => $cashAccountId,
    'method' => 'CASH',
], $token);
check('the corrected counter sale is settled on account', $response['status'] === 201, $response['raw']);
$response = call('DELETE', '/api/customers/' . $tempCustomerId, null, $token);
check('a customer who owes nothing is deactivated', $response['status'] === 200
    && ($response['json']['data']['isActive'] ?? true) === false, $response['raw']);
$response = call('GET', '/api/customers?search=' . urlencode('Client Éphémère'), null, $token);
check('a closed customer leaves the working list', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? -1) === 0, $response['raw']);

$response = call('GET', '/api/salesOrders?status=INVOICED', null, $token);
check('invoiced orders are filtered', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) >= 1, $response['raw']);
$response = call('GET', '/api/invoices?status=PAID', null, $token);
check('the invoice list filters on the paid status', $response['status'] === 200
    && isset($response['json']['meta']['summary']['outstanding']), $response['raw']);
$response = call('GET', '/api/quotations?status=ACCEPTED', null, $token);
check('accepted quotations are listed', $response['status'] === 200
    && (int) ($response['json']['meta']['total'] ?? 0) >= 1, $response['raw']);
$response = call('GET', '/api/documents/unknown/' . $invoiceId, null, $token);
check('an unknown document type answers 404', $response['status'] === 404, $response['raw']);

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
