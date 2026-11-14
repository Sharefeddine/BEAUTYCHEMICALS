<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Customers: the file, the receivable and the statement.
 */
final class CustomerController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $segment = $request->queryString('priceSegment') !== '' ? $request->queryString('priceSegment')
            : ($request->queryString('segment') !== '' ? $request->queryString('segment') : null);

        $result = $this->app->customers->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $segment,
            $request->queryString('activeOnly') === '1'
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function selectable(Request $request): Response
    {
        return Response::ok($this->app->customers->selectable());
    }

    public function show(Request $request): Response
    {
        return Response::ok($this->app->customers->find((int) $request->params['id']));
    }

    public function statement(Request $request): Response
    {
        $from = $request->queryString('from');
        $to = $request->queryString('to');

        return Response::ok($this->app->customers->statement(
            (int) $request->params['id'],
            $from !== '' ? $from : null,
            $to !== '' ? $to : null
        ));
    }

    public function store(Request $request): Response
    {
        $created = $this->app->customers->create($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'CUSTOMERS', 'customers', $created['id'],
            ['name' => $created['name'], 'creditLimit' => $created['creditLimit'],
                'priceSegment' => $created['priceSegment']], 'INFO', $request->ip, $created['code']);

        return Response::created($created);
    }

    public function update(Request $request): Response
    {
        $updated = $this->app->customers->update((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'CUSTOMERS', 'customers', $updated['id'],
            ['creditLimit' => $updated['creditLimit'], 'priceSegment' => $updated['priceSegment']], 'INFO',
            $request->ip, $updated['code']);

        return Response::ok($updated);
    }

    public function destroy(Request $request): Response
    {
        $customer = $this->app->customers->deactivate((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CLOSE', 'CUSTOMERS', 'customers', $customer['id'],
            ['balance' => $customer['balance']], 'WARNING', $request->ip, $customer['code']);

        return Response::ok($customer);
    }
}
