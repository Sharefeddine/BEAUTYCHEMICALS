<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Financial statements: margin by product, cash flow, and the result of a month.
 */
final class ReportController
{
    public function __construct(private readonly App $app)
    {
    }

    public function margin(Request $request): Response
    {
        return Response::ok($this->app->reports->margin(
            $request->queryString('from'),
            $request->queryString('to'),
            min(200, max(1, $request->queryInt('limit', 50)))
        ));
    }

    public function cashflow(Request $request): Response
    {
        return Response::ok($this->app->reports->cashflow(
            $request->queryString('from'),
            $request->queryString('to')
        ));
    }

    public function result(Request $request): Response
    {
        $period = $request->queryString('period', date('Y-m'));

        return Response::ok($this->app->reports->result($period));
    }
}
