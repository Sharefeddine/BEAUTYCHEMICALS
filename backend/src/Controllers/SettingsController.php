<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

final class SettingsController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        return Response::ok([
            'settings' => $this->app->settings->grouped(),
            'company' => $this->app->settings->company(),
        ]);
    }

    public function update(Request $request): Response
    {
        $values = $request->body['settings'] ?? $request->body;
        if (!is_array($values)) {
            throw ApiException::validation(['settings' => 'validation.required']);
        }
        $flat = [];
        foreach ($values as $key => $value) {
            if (!is_string($key)) {
                continue;
            }
            $flat[$key] = is_bool($value) ? $value : (string) $value;
        }
        if ($flat === []) {
            throw ApiException::validation(['settings' => 'validation.required']);
        }

        $changed = $this->app->settings->updateMany($flat, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'SETTINGS', 'settings', null,
            ['changed' => array_keys($flat), 'count' => $changed], 'INFO', $request->ip);

        return Response::ok(['updated' => $changed, 'settings' => $this->app->settings->grouped()]);
    }

    public function updateCompany(Request $request): Response
    {
        $company = $this->app->settings->updateCompany($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'SETTINGS', 'companies',
            $company === null ? null : (int) $company['id'], [], 'INFO', $request->ip);

        if ($company === null) {
            throw ApiException::notFound('Company');
        }

        return Response::ok($company);
    }
}
