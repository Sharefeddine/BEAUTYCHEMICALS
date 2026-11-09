<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

final class MaterialController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->materials->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryInt('categoryId') > 0 ? $request->queryInt('categoryId') : null,
            $request->queryString('sort', 'commercialName'),
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
        return Response::ok($this->app->materials->find((int) $request->params['id']));
    }

    public function batches(Request $request): Response
    {
        return Response::ok($this->app->materials->batches((int) $request->params['id']));
    }

    public function update(Request $request): Response
    {
        (new Validator($request->body))
            ->decimal('purchasePrice', 0, 999999999)
            ->decimal('minStock', 0, 999999999)
            ->pattern('hazardPictograms', '/^(GHS0[1-9])?(,GHS0[1-9])*$/')
            ->validate();

        $updated = $this->app->materials->update((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'MATERIALS', 'rawMaterials', $updated['id'],
            array_keys($request->body), 'INFO', $request->ip, $updated['code']);

        return Response::ok($updated);
    }

    public function categories(Request $request): Response
    {
        return Response::ok($this->app->materials->categories());
    }
}
