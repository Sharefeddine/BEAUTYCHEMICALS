<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * The selling side: quotations, orders and the delivery notes that consume stock FEFO.
 */
final class SalesController
{
    public function __construct(private readonly App $app)
    {
    }

    // ---------------------------------------------------------------- quotations

    public function quotations(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->sales->paginateQuotations(
            $page,
            $pageSize,
            $request->queryString('search'),
            $this->nullable($request->queryString('status')),
            $this->nullableId($request->queryInt('customerId')),
            $this->nullable($request->queryString('from')),
            $this->nullable($request->queryString('to'))
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']));
    }

    public function quotation(Request $request): Response
    {
        return Response::ok($this->app->sales->quotation((int) $request->params['id']));
    }

    public function storeQuotation(Request $request): Response
    {
        $created = $this->app->sales->createQuotation($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'SALES', 'quotations', $created['id'],
            ['customerId' => $created['customerId'], 'totalTtc' => $created['totalTtc']], 'INFO', $request->ip,
            $created['quoteNumber']);

        return Response::created($created);
    }

    public function sendQuotation(Request $request): Response
    {
        $quotation = $this->app->sales->sendQuotation((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'SEND', 'SALES', 'quotations', $quotation['id'],
            ['status' => $quotation['status']], 'INFO', $request->ip, $quotation['quoteNumber']);

        return Response::ok($quotation);
    }

    public function acceptQuotation(Request $request): Response
    {
        $result = $this->app->sales->acceptQuotation((int) $request->params['id'], (int) $request->user['id'],
            isset($request->body['deliveryDate']) && $request->body['deliveryDate'] !== ''
                ? (string) $request->body['deliveryDate'] : null);
        $this->app->audit->log($request->user, 'ACCEPT', 'SALES', 'quotations', $result['quotation']['id'],
            ['orderNumber' => $result['order']['orderNumber']], 'INFO', $request->ip,
            $result['quotation']['quoteNumber']);

        return Response::ok($result);
    }

    public function rejectQuotation(Request $request): Response
    {
        $quotation = $this->app->sales->rejectQuotation((int) $request->params['id']);
        $this->app->audit->log($request->user, 'REJECT', 'SALES', 'quotations', $quotation['id'],
            ['status' => $quotation['status']], 'INFO', $request->ip, $quotation['quoteNumber']);

        return Response::ok($quotation);
    }

    // -------------------------------------------------------------------- orders

    public function orders(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->sales->paginateOrders(
            $page,
            $pageSize,
            $request->queryString('search'),
            $this->nullable($request->queryString('status')),
            $this->nullableId($request->queryInt('customerId')),
            $this->nullable($request->queryString('from')),
            $this->nullable($request->queryString('to'))
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']));
    }

    public function order(Request $request): Response
    {
        return Response::ok($this->app->sales->order((int) $request->params['id']));
    }

    public function storeOrder(Request $request): Response
    {
        $created = $this->app->sales->createOrder($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'SALES', 'salesOrders', $created['id'],
            ['customerId' => $created['customerId'], 'totalTtc' => $created['totalTtc']], 'INFO', $request->ip,
            $created['orderNumber']);

        return Response::created($created);
    }

    public function confirmOrder(Request $request): Response
    {
        $order = $this->app->sales->confirmOrder((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CONFIRM', 'SALES', 'salesOrders', $order['id'],
            ['status' => $order['status'], 'totalTtc' => $order['totalTtc']], 'INFO', $request->ip,
            $order['orderNumber']);

        return Response::ok($order);
    }

    public function cancelOrder(Request $request): Response
    {
        $order = $this->app->sales->cancelOrder((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CANCEL', 'SALES', 'salesOrders', $order['id'],
            ['status' => $order['status']], 'WARNING', $request->ip, $order['orderNumber']);

        return Response::ok($order);
    }

    // ---------------------------------------------------------------- deliveries

    public function deliveries(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->sales->paginateDeliveries(
            $page,
            $pageSize,
            $request->queryString('search'),
            $this->nullable($request->queryString('status')),
            $this->nullableId($request->queryInt('customerId')),
            $this->nullable($request->queryString('from')),
            $this->nullable($request->queryString('to'))
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']));
    }

    public function delivery(Request $request): Response
    {
        return Response::ok($this->app->sales->delivery((int) $request->params['id']));
    }

    public function storeDelivery(Request $request): Response
    {
        $created = $this->app->sales->createDelivery((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'DELIVER', 'DISTRIBUTION', 'deliveryNotes', $created['id'],
            ['orderNumber' => $created['orderNumber'], 'quantity' => $created['totalQuantity']], 'INFO',
            $request->ip, $created['noteNumber']);

        return Response::created($created);
    }

    public function loadDelivery(Request $request): Response
    {
        $delivery = $this->app->sales->loadDelivery((int) $request->params['id']);
        $this->app->audit->log($request->user, 'LOAD', 'DISTRIBUTION', 'deliveryNotes', $delivery['id'],
            ['status' => $delivery['status']], 'INFO', $request->ip, $delivery['noteNumber']);

        return Response::ok($delivery);
    }

    public function deliverDelivery(Request $request): Response
    {
        $delivery = $this->app->sales->deliverDelivery((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'SIGN', 'DISTRIBUTION', 'deliveryNotes', $delivery['id'],
            ['signedBy' => $delivery['signedBy']], 'INFO', $request->ip, $delivery['noteNumber']);

        return Response::ok($delivery);
    }

    public function returnDelivery(Request $request): Response
    {
        $delivery = $this->app->sales->returnDelivery((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'RETURN', 'DISTRIBUTION', 'deliveryNotes', $delivery['id'],
            ['reason' => $delivery['notes']], 'WARNING', $request->ip, $delivery['noteNumber']);

        return Response::ok($delivery);
    }

    private function nullable(string $value): ?string
    {
        return $value === '' ? null : $value;
    }

    private function nullableId(int $value): ?int
    {
        return $value > 0 ? $value : null;
    }

    /** @return array<string,int> */
    private function meta(int $page, int $pageSize, int $total): array
    {
        return [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $total,
            'totalPages' => (int) ceil($total / max(1, $pageSize)),
        ];
    }
}
