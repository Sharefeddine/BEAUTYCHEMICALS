<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * The treasury: the accounting calendar, the cash book and what the money did.
 */
final class FinanceController
{
    public function __construct(private readonly App $app)
    {
    }

    public function periods(Request $request): Response
    {
        return Response::ok($this->app->finance->periods());
    }

    public function period(Request $request): Response
    {
        return Response::ok($this->app->finance->period((int) $request->params['id']));
    }

    public function closePeriod(Request $request): Response
    {
        $period = $this->app->finance->closePeriod((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CLOSE', 'FINANCE', 'accountingPeriods', $period['id'],
            ['code' => $period['code'], 'totalIn' => $period['totalIn'], 'totalOut' => $period['totalOut']],
            'WARNING', $request->ip, $period['code']);

        return Response::ok($period);
    }

    public function reopenPeriod(Request $request): Response
    {
        $period = $this->app->finance->reopenPeriod((int) $request->params['id']);
        $this->app->audit->log($request->user, 'REOPEN', 'FINANCE', 'accountingPeriods', $period['id'],
            ['code' => $period['code']], 'WARNING', $request->ip, $period['code']);

        return Response::ok($period);
    }

    public function cashBook(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $accountId = $request->queryInt('cashAccountId', 0);
        $result = $this->app->finance->paginateCashBook(
            $page,
            $pageSize,
            $request->queryString('search'),
            $accountId > 0 ? $accountId : null,
            $request->queryString('direction'),
            $request->queryString('from'),
            $request->queryString('to'),
            $request->queryString('sourceType')
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
            'summary' => $result['totals'],
        ]);
    }

    public function summary(Request $request): Response
    {
        $from = $request->queryString('from');
        $to = $request->queryString('to');
        $period = $this->app->finance->summary($from !== '' ? $from : null, $to !== '' ? $to : null);
        $treasury = $this->app->finance->treasury($from !== '' ? $from : null, $to !== '' ? $to : null);

        return Response::ok($period + ['treasury' => $treasury]);
    }

    public function storeTransaction(Request $request): Response
    {
        $body = $request->body;
        $transaction = $this->app->finance->record([
            'cashAccountId' => (int) ($body['cashAccountId'] ?? 0),
            'direction' => (string) ($body['direction'] ?? 'IN'),
            'amount' => $body['amount'] ?? 0,
            'sourceType' => (string) ($body['sourceType'] ?? 'OTHER'),
            'description' => (string) ($body['description'] ?? ''),
            'occurredAt' => $body['occurredAt'] ?? null,
            'userId' => (int) $request->user['id'],
        ]);
        $this->app->audit->log($request->user, 'CREATE', 'FINANCE', 'cashTransactions', $transaction['id'],
            ['direction' => $transaction['direction'], 'amount' => $transaction['amount']],
            'INFO', $request->ip, $transaction['description']);

        return Response::created($transaction);
    }
}
