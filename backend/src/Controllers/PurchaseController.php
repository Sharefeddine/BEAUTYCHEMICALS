<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

/**
 * Purchasing: orders, their lifecycle and the goods reception that puts stock on the shelves.
 */
final class PurchaseController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->purchases->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryString('status'),
            $request->queryInt('supplierId') > 0 ? $request->queryInt('supplierId') : null,
            $request->queryString('from') !== '' ? $request->queryString('from') : null,
            $request->queryString('to') !== '' ? $request->queryString('to') : null
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
        return Response::ok($this->app->purchases->find((int) $request->params['id']));
    }

    public function store(Request $request): Response
    {
        (new Validator($request->body))
            ->integer('supplierId', 1, PHP_INT_MAX)
            ->pattern('orderDate', '/^\d{4}-\d{2}-\d{2}$/', 'validation.date')
            ->pattern('expectedDate', '/^\d{4}-\d{2}-\d{2}$/', 'validation.date')
            ->validate();
        if (!is_array($request->body['items'] ?? null)) {
            throw ApiException::validation(['items' => 'validation.required']);
        }

        $created = $this->app->purchases->create($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'PURCHASING', 'purchaseOrders', $created['id'],
            ['supplierId' => $created['supplierId'], 'totalTtc' => $created['totalTtc']], 'INFO', $request->ip,
            $created['orderNumber']);

        return Response::created($created);
    }

    public function send(Request $request): Response
    {
        $order = $this->app->purchases->send((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'SEND', 'PURCHASING', 'purchaseOrders', $order['id'],
            ['status' => $order['status']], 'INFO', $request->ip, $order['orderNumber']);

        return Response::ok($order);
    }

    public function cancel(Request $request): Response
    {
        $order = $this->app->purchases->cancel((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CANCEL', 'PURCHASING', 'purchaseOrders', $order['id'],
            ['status' => $order['status'], 'reason' => $request->string('reason')], 'WARNING', $request->ip,
            $order['orderNumber']);

        return Response::ok($order);
    }

    public function receive(Request $request): Response
    {
        (new Validator($request->body))
            ->integer('warehouseId', 1, PHP_INT_MAX)
            ->validate();
        if (!isset($request->body['warehouseId'])) {
            throw ApiException::validation(['warehouseId' => 'validation.required']);
        }
        if (!is_array($request->body['items'] ?? null)) {
            throw ApiException::validation(['items' => 'validation.required']);
        }

        $receipt = $this->app->purchases->receive((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'RECEIVE', 'PURCHASING', 'goodsReceipts', $receipt['id'],
            ['orderId' => $receipt['orderId'], 'warehouseId' => $receipt['warehouseId'],
                'itemCount' => count($receipt['items']), 'orderStatus' => $receipt['orderStatus']],
            'INFO', $request->ip, $receipt['receiptNumber']);

        return Response::created($receipt);
    }
}
