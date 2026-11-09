<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

final class ProductController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->products->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryInt('categoryId') > 0 ? $request->queryInt('categoryId') : null,
            $request->queryString('type'),
            $request->queryString('status'),
            $request->queryString('sort', 'nameFr'),
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
        return Response::ok($this->app->products->find((int) $request->params['id']));
    }

    public function store(Request $request): Response
    {
        $data = $this->validated($request, true);
        $created = $this->app->products->create($data, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'PRODUCTS', 'products', $created['id'],
            ['sku' => $created['sku']], 'INFO', $request->ip, $created['sku']);

        return Response::created($created);
    }

    public function update(Request $request): Response
    {
        $data = $this->validated($request, false);
        $updated = $this->app->products->update((int) $request->params['id'], $data, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'PRODUCTS', 'products', $updated['id'],
            array_keys($data), 'INFO', $request->ip, $updated['sku']);

        return Response::ok($updated);
    }

    public function destroy(Request $request): Response
    {
        $id = (int) $request->params['id'];
        $this->app->products->softDelete($id, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'DELETE', 'PRODUCTS', 'products', $id, [], 'WARNING', $request->ip);

        return Response::ok(['deleted' => true, 'id' => $id]);
    }

    public function categories(Request $request): Response
    {
        return Response::ok($this->app->products->categories());
    }

    private function validated(Request $request, bool $creating): array
    {
        $validator = (new Validator($request->body))
            ->integer('categoryId', 1, PHP_INT_MAX)
            ->decimal('wholesalePrice', 0, 999999999)
            ->decimal('retailPrice', 0, 999999999)
            ->decimal('vatRate', 0, 100)
            ->integer('minStock', 0, 1000000)
            ->inList('type', ['MANUFACTURED', 'TRADED', 'RAW_MATERIAL', 'PACKAGING']);
        if ($creating) {
            $validator->required('sku', 30)->required('nameFr', 150)->required('nameAr', 150);
        }
        $validator->validate();

        $data = array_filter([
            'sku' => $request->string('sku') ?: null,
            'barcode' => $request->string('barcode') ?: null,
            'nameFr' => $request->string('nameFr') ?: null,
            'nameAr' => $request->string('nameAr') ?: null,
            'description' => $request->string('description'),
            'categoryId' => $request->int('categoryId') ?: null,
            'type' => $request->string('type') ?: null,
            'unit' => $request->string('unit') ?: null,
            'packaging' => $request->string('packaging'),
            'packagingCapacity' => $request->body['packagingCapacity'] ?? null,
            'capacityUnit' => $request->string('capacityUnit') ?: null,
            'unitsPerCarton' => $request->body['unitsPerCarton'] ?? null,
            'minStock' => $request->body['minStock'] ?? null,
            'maxStock' => $request->body['maxStock'] ?? null,
            'purchaseCost' => $request->body['purchaseCost'] ?? null,
            'productionCost' => $request->body['productionCost'] ?? null,
            'wholesalePrice' => $request->body['wholesalePrice'] ?? null,
            'retailPrice' => $request->body['retailPrice'] ?? null,
            'specialPrice' => $request->body['specialPrice'] ?? null,
            'vatRate' => $request->body['vatRate'] ?? null,
            'isActive' => array_key_exists('isActive', $request->body) ? $request->boolean('isActive', true) : null,
        ], static fn ($value): bool => $value !== null);

        if (!$creating && $data === []) {
            throw ApiException::validation(['body' => 'validation.required']);
        }

        return $data;
    }
}
