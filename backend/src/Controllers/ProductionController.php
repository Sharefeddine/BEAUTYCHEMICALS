<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

/**
 * Production: manufacturing orders, consumption, quality release and batch genealogy.
 *
 * <p>Every endpoint here is a recording act, never a computation of the formula: the shop floor
 * starts from a version a manager approved, reports what it used, and quality decides what may be
 * sold.</p>
 */
final class ProductionController
{
    public function __construct(private readonly App $app)
    {
    }

    /** Approved versions the shop floor may produce — the create dialog's only source. */
    public function versions(Request $request): Response
    {
        return Response::ok($this->app->production->versions());
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->production->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryString('status'),
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
        return Response::ok($this->app->production->find((int) $request->params['id']));
    }

    public function store(Request $request): Response
    {
        (new Validator($request->body))
            ->integer('formulationVersionId', 1, PHP_INT_MAX)
            ->integer('warehouseId', 1, PHP_INT_MAX)
            ->decimal('plannedQuantity', 0.001, 999999999)
            ->pattern('plannedDate', '/^\d{4}-\d{2}-\d{2}$/', 'validation.date')
            ->validate();

        $order = $this->app->production->create($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'PRODUCTION', 'productionOrders', $order['id'],
            ['productId' => $order['productId'], 'plannedQuantity' => $order['plannedQuantity'],
                'formulationVersionId' => $order['formulationVersionId']], 'INFO', $request->ip,
            $order['orderNumber']);

        return Response::created($order);
    }

    public function start(Request $request): Response
    {
        $order = $this->app->production->start((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'START', 'PRODUCTION', 'productionOrders', $order['id'],
            ['status' => $order['status'], 'materialLines' => count($order['materials'])], 'INFO',
            $request->ip, $order['orderNumber']);

        return Response::ok($order);
    }

    public function consume(Request $request): Response
    {
        if (!is_array($request->body['items'] ?? null)) {
            throw ApiException::validation(['items' => 'validation.required']);
        }
        $order = $this->app->production->consume((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CONSUME', 'PRODUCTION', 'productionOrders', $order['id'],
            ['materialCost' => $order['materialCost'], 'consumedQuantity' => $order['consumedQuantity']],
            'INFO', $request->ip, $order['orderNumber']);

        return Response::ok($order);
    }

    public function complete(Request $request): Response
    {
        (new Validator($request->body))
            ->decimal('actualQuantity', 0.001, 999999999)
            ->pattern('productionDate', '/^\d{4}-\d{2}-\d{2}$/', 'validation.date')
            ->pattern('expiryDate', '/^\d{4}-\d{2}-\d{2}$/', 'validation.date')
            ->validate();

        $order = $this->app->production->complete((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'COMPLETE', 'PRODUCTION', 'productionOrders', $order['id'],
            ['actualQuantity' => $order['actualQuantity'], 'wasteQuantity' => $order['wasteQuantity'],
                'totalCost' => $order['totalCost'], 'costPerUnit' => $order['costPerUnit']], 'INFO',
            $request->ip, $order['orderNumber']);

        return Response::ok($order);
    }

    public function quality(Request $request): Response
    {
        (new Validator($request->body))
            ->required('checkType', 40)
            ->inList('result', ['PASS', 'FAIL', 'pass', 'fail'])
            ->validate();

        $order = $this->app->production->quality((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $result = strtoupper((string) $request->body['result']);
        $this->app->audit->log($request->user, $result === 'PASS' ? 'RELEASE' : 'REJECT', 'PRODUCTION',
            'productionOrders', $order['id'],
            ['result' => $result, 'status' => $order['status'], 'checkType' => $request->string('checkType')],
            $result === 'PASS' ? 'INFO' : 'WARNING', $request->ip, $order['orderNumber']);

        return Response::ok($order);
    }

    public function cancel(Request $request): Response
    {
        $order = $this->app->production->cancel((int) $request->params['id'], $request->string('reason'),
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CANCEL', 'PRODUCTION', 'productionOrders', $order['id'],
            ['status' => $order['status'], 'reason' => $request->string('reason')], 'WARNING', $request->ip,
            $order['orderNumber']);

        return Response::ok($order);
    }

    public function genealogy(Request $request): Response
    {
        return Response::ok($this->app->production->genealogy((int) $request->params['id']));
    }
}
