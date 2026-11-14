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

    public function catalogue(Request $request): Response
    {
        return Response::ok($this->app->reports->catalogue());
    }

    public function sales(Request $request): Response
    {
        return Response::ok($this->app->reports->sales($request->queryString('from'),
            $request->queryString('to')));
    }

    public function purchases(Request $request): Response
    {
        return Response::ok($this->app->reports->purchases($request->queryString('from'),
            $request->queryString('to')));
    }

    public function stock(Request $request): Response
    {
        return Response::ok($this->app->reports->stock(
            min(365, max(1, $request->queryInt('expiryDays', 90))),
            min(200, max(1, $request->queryInt('limit', 40)))
        ));
    }

    public function production(Request $request): Response
    {
        return Response::ok($this->app->reports->production($request->queryString('from'),
            $request->queryString('to')));
    }

    public function safety(Request $request): Response
    {
        return Response::ok($this->app->reports->safety());
    }
}
