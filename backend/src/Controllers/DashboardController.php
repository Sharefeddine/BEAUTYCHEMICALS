<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

final class DashboardController
{
    public function __construct(private readonly App $app)
    {
    }

    /**
     * GET /api/dashboard/summary?from=YYYY-MM-DD&to=YYYY-MM-DD
     * Defaults to the current month, which is what the dashboard opens on.
     */
    public function summary(Request $request): Response
    {
        $from = $request->queryString('from', date('Y-m-01'));
        $to = $request->queryString('to', date('Y-m-t'));

        foreach ([$from, $to] as $date) {
            if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date) || strtotime($date) === false) {
                throw ApiException::validation(['period' => 'validation.format'],
                    'Dates must be formatted as YYYY-MM-DD.');
            }
        }
        if ($from > $to) {
            throw ApiException::validation(['period' => 'validation.range'],
                'The start date must be before the end date.');
        }

        return Response::ok($this->app->dashboard->summary($from, $to));
    }
}
