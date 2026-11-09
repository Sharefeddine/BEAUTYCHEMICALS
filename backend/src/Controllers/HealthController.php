<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Unauthenticated health probe used by the desktop client (AUTO data mode) and by monitoring.
 * It never leaks connection details: only up/down and the schema version.
 */
final class HealthController
{
    public const API_VERSION = '2.0.0';

    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $database = 'ok';
        $tables = null;
        try {
            $tables = (int) $this->app->db->scalar(
                'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = :schema',
                ['schema' => (string) $this->app->config->get('database.name')]
            );
        } catch (\Throwable $exception) {
            $database = 'down';
        }

        $response = Response::ok([
            'status' => $database === 'ok' ? 'ok' : 'degraded',
            'database' => $database,
            'apiVersion' => self::API_VERSION,
            'tables' => $tables,
            'serverTime' => date('c'),
        ]);

        return $database === 'ok' ? $response : $response->status(503);
    }
}
