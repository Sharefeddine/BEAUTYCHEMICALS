<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Suppliers: master data, what they sell us and their purchasing history.
 */
final class SupplierController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->suppliers->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            in_array($request->queryString('activeOnly'), ['1', 'true', 'yes'], true),
            $request->queryString('sort', 'name'),
            $request->queryString('direction', 'ASC')
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function show(Request $request): Response
    {
        return Response::ok($this->app->suppliers->find((int) $request->params['id']));
    }
}
