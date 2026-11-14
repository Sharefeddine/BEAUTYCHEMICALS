<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Billing: invoices (frozen once issued), the payments that settle them and the credit notes that
 * correct them.
 */
final class BillingController
{
    public function __construct(private readonly App $app)
    {
    }

    // ------------------------------------------------------------------ invoices

    public function invoices(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->sales->paginateInvoices(
            $page,
            $pageSize,
            $request->queryString('search'),
            $this->nullable($request->queryString('status')),
            $this->nullableId($request->queryInt('customerId')),
            $this->nullable($request->queryString('from')),
            $this->nullable($request->queryString('to'))
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']) + [
            'summary' => $result['summary'],
        ]);
    }

    public function invoice(Request $request): Response
    {
        return Response::ok($this->app->sales->invoice((int) $request->params['id']));
    }

    public function storeInvoice(Request $request): Response
    {
        $created = $this->app->sales->createInvoice($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'FINANCE', 'invoices', $created['id'],
            ['customerId' => $created['customerId'], 'totalTtc' => $created['totalTtc']], 'INFO', $request->ip,
            $created['invoiceNumber']);

        return Response::created($created);
    }

    public function updateInvoice(Request $request): Response
    {
        $invoice = $this->app->sales->updateInvoice((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'FINANCE', 'invoices', $invoice['id'],
            ['totalTtc' => $invoice['totalTtc']], 'INFO', $request->ip, $invoice['invoiceNumber']);

        return Response::ok($invoice);
    }

    public function issueInvoice(Request $request): Response
    {
        $invoice = $this->app->sales->issueInvoice((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'ISSUE', 'FINANCE', 'invoices', $invoice['id'],
            ['totalTtc' => $invoice['totalTtc'], 'customerId' => $invoice['customerId']], 'INFO', $request->ip,
            $invoice['invoiceNumber']);

        return Response::ok($invoice);
    }

    public function cancelInvoice(Request $request): Response
    {
        $invoice = $this->app->sales->cancelInvoice((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CANCEL', 'FINANCE', 'invoices', $invoice['id'],
            ['status' => $invoice['status']], 'WARNING', $request->ip, $invoice['invoiceNumber']);

        return Response::ok($invoice);
    }

    // ------------------------------------------------------------------ payments

    public function payments(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->sales->paginatePayments(
            $page,
            $pageSize,
            $this->nullableId($request->queryInt('customerId')),
            $this->nullableId($request->queryInt('invoiceId')),
            $request->queryString('search'),
            $this->nullable($request->queryString('method'))
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']) + [
            'summary' => $result['summary'],
        ]);
    }

    public function storePayment(Request $request): Response
    {
        $created = $this->app->sales->createPayment($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'PAYMENT', 'FINANCE', 'payments', $created['id'],
            ['amount' => $created['amount'], 'invoiceNumber' => $created['invoiceNumber']], 'INFO', $request->ip,
            $created['paymentNumber']);

        return Response::created($created);
    }

    public function cashAccounts(Request $request): Response
    {
        return Response::ok($this->app->sales->cashAccounts());
    }

    // -------------------------------------------------------------- credit notes

    public function creditNotes(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->sales->paginateCreditNotes(
            $page,
            $pageSize,
            $this->nullableId($request->queryInt('customerId')),
            $this->nullable($request->queryString('status'))
        );

        return Response::ok($result['rows'], $this->meta($page, $pageSize, $result['total']));
    }

    public function creditNote(Request $request): Response
    {
        return Response::ok($this->app->sales->creditNote((int) $request->params['id']));
    }

    public function storeCreditNote(Request $request): Response
    {
        $created = $this->app->sales->createCreditNote((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREDIT_NOTE', 'FINANCE', 'creditNotes', $created['id'],
            ['invoiceNumber' => $created['invoiceNumber'], 'totalTtc' => $created['totalTtc']], 'WARNING',
            $request->ip, $created['creditNumber']);

        return Response::created($created);
    }

    public function applyCreditNote(Request $request): Response
    {
        $note = $this->app->sales->applyCreditNote((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'APPLY_CREDIT', 'FINANCE', 'creditNotes', $note['id'],
            ['totalTtc' => $note['totalTtc'], 'invoiceNumber' => $note['invoiceNumber']], 'INFO', $request->ip,
            $note['creditNumber']);

        return Response::ok($note);
    }

    public function cancelCreditNote(Request $request): Response
    {
        $note = $this->app->sales->cancelCreditNote((int) $request->params['id']);
        $this->app->audit->log($request->user, 'CANCEL_CREDIT', 'FINANCE', 'creditNotes', $note['id'],
            ['status' => $note['status']], 'WARNING', $request->ip, $note['creditNumber']);

        return Response::ok($note);
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
