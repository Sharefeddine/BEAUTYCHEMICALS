<?php

declare(strict_types=1);

/**
 * Front controller: the only file exposed by the web server (document root = backend/public).
 */

require dirname(__DIR__) . '/src/autoload.php';

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

ini_set('display_errors', '0');
error_reporting(E_ALL);

$request = null;
try {
    $app = new App();
    $request = Request::fromGlobals();
    $app->applyCors($request);

    if ($request->method === 'OPTIONS') {
        http_response_code(204);

        return;
    }

    $app->handle($request)->send();
} catch (ApiException $exception) {
    Response::error($exception->status, $exception->errorCode, $exception->getMessage(), $exception->details)->send();
} catch (Throwable $exception) {
    error_log('chemmanager-api: ' . $exception->getMessage() . ' @ ' . $exception->getFile() . ':' . $exception->getLine());
    Response::error(500, 'INTERNAL_ERROR', 'An unexpected error occurred.')->send();
}
