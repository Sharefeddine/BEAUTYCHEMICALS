<?php

declare(strict_types=1);

/**
 * Unit tests that need no database: they run with a bare `php` binary, which is exactly what CI
 * and the developer workstation do. Integration tests live in tests/integration.
 *
 * Usage: php backend/tests/unit/run.php
 */

require dirname(__DIR__, 2) . '/src/autoload.php';

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Config;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Router;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Security\Authorizer;

$passed = 0;
$failures = [];

function check(string $name, bool $condition, string $detail = ''): void
{
    global $passed, $failures;
    if ($condition) {
        $passed++;

        return;
    }
    $failures[] = $name . ($detail !== '' ? ' - ' . $detail : '');
}

// ---------------------------------------------------------------- Router
$router = new Router();
$router->add('GET', '/api/products', static fn (): string => 'index', 'PRODUCTS_VIEW');
$router->add('GET', '/api/products/{id}', static fn (): string => 'show', 'PRODUCTS_VIEW');
$router->add('PUT', '/api/products/{id}', static fn (): string => 'update', 'PRODUCTS_EDIT');
$router->add('GET', '/api/rawMaterials/{id}/batches', static fn (): string => 'batches', 'INVENTORY_VIEW');

$match = $router->match('GET', '/api/products');
check('router matches a static route', $match['permission'] === 'PRODUCTS_VIEW');
$match = $router->match('GET', '/api/products/42');
check('router extracts a path parameter', $match['params']['id'] === '42');
$match = $router->match('GET', '/api/rawMaterials/7/batches');
check('router handles two-segment parameters', $match['params']['id'] === '7');
try {
    $router->match('GET', '/api/products/42/unknown');
    check('router rejects unknown paths', false);
} catch (ApiException $exception) {
    check('router rejects unknown paths', $exception->status === 404 && $exception->errorCode === 'ROUTE_NOT_FOUND');
}
try {
    $router->match('DELETE', '/api/products/42');
    check('router answers 405 with the allowed methods', false);
} catch (ApiException $exception) {
    check('router answers 405 with the allowed methods',
        $exception->status === 405 && in_array('GET', $exception->details['allowed'], true));
}
check('router counts its routes', $router->count() === 4);

// ---------------------------------------------------------------- Response envelope
$ok = Response::ok(['amountHt' => 1200.5], ['page' => 1, 'total' => 3]);
$payload = json_decode($ok->json(), true);
check('success envelope carries data', $payload['success'] === true && $payload['data']['amountHt'] === 1200.5);
check('success envelope carries meta', $payload['meta']['total'] === 3);
$error = Response::error(422, 'VALIDATION_FAILED', 'Invalid.', ['sku' => 'validation.required']);
$payload = json_decode($error->json(), true);
check('error envelope carries the code', $payload['success'] === false && $payload['error']['code'] === 'VALIDATION_FAILED');
check('error envelope keeps the status', $error->getStatus() === 422);
check('error envelope lists field errors', $payload['error']['details']['sku'] === 'validation.required');
check('json keeps camelCase and arabic text',
    json_decode(Response::ok(['nameAr' => 'سائل'])->json(), true)['data']['nameAr'] === 'سائل');

// ---------------------------------------------------------------- Validator
$validator = (new Validator(['sku' => '', 'email' => 'nope', 'price' => -3, 'type' => 'X']))
    ->required('sku')->email('email')->decimal('price', 0, 100)->inList('type', ['A', 'B']);
check('validator collects every error', count($validator->errors()) === 4);
check('validator reports the expected keys',
    array_keys($validator->errors()) === ['sku', 'email', 'price', 'type']);
$validator = (new Validator(['sku' => 'ABC-1', 'email' => 'a@b.dz', 'price' => 10, 'type' => 'A']))
    ->required('sku', 30)->email('email')->decimal('price', 0, 100)->inList('type', ['A', 'B']);
check('validator accepts valid input', $validator->passes());

// ---------------------------------------------------------------- Authorizer
$authorizer = new Authorizer();
$authorizer->assert('PRODUCTS_VIEW', ['PRODUCTS_VIEW'], []);
check('authorizer accepts a granted permission', true);
$authorizer->assert('ANYTHING', [], ['SUPER_ADMIN']);
check('authorizer accepts the super administrator', true);
try {
    $authorizer->assert('FINANCE_CLOSE_PERIOD', ['PRODUCTS_VIEW'], ['SALES_MANAGER']);
    check('authorizer refuses a missing permission', false);
} catch (ApiException $exception) {
    check('authorizer refuses a missing permission',
        $exception->status === 403 && str_contains($exception->getMessage(), 'FINANCE_CLOSE_PERIOD'));
}

// ---------------------------------------------------------------- Request helpers
$request = new Request('POST', '/api/products', ['page' => '2'],
    ['sku' => '  ABC  ', 'quantity' => '5', 'isActive' => 'true']);
check('request trims strings', $request->string('sku') === 'ABC');
check('request casts integers', $request->int('quantity') === 5);
check('request casts booleans', $request->boolean('isActive') === true);
check('request reads the query string', $request->queryInt('page') === 2);
$bearer = new Request('GET', '/api/auth/me', [], [], ['authorization' => 'Bearer abcdef']);
check('request reads the bearer token', $bearer->bearerToken() === 'abcdef');

// ---------------------------------------------------------------- Config
$config = Config::load(__DIR__ . '/nonexistent.php');
check('config falls back to defaults', $config->get('database.port') === 3306);
check('config exposes its security defaults', $config->get('security.tokenTtlHours') === 8);

// ---------------------------------------------------------------- report
if ($failures === []) {
    echo 'unit tests: ' . $passed . ' passed' . PHP_EOL;
    exit(0);
}
echo 'unit tests: ' . $passed . ' passed, ' . count($failures) . ' failed' . PHP_EOL;
foreach ($failures as $failure) {
    echo '  FAILED: ' . $failure . PHP_EOL;
}
exit(1);
