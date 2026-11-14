<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Operating expenses: entry, approval, payment, and what they cost per category.
 */
final class ExpenseController
{
    public function __construct(private readonly App $app)
    {
    }

    public function categories(Request $request): Response
    {
        return Response::ok($this->app->expenses->categories());
    }

    public function expenses(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $categoryId = $request->queryInt('categoryId', 0);
        $result = $this->app->expenses->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $categoryId > 0 ? $categoryId : null,
            $request->queryString('status'),
            $request->queryString('from'),
            $request->queryString('to')
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
            'summary' => $result['summary'],
        ]);
    }

    public function expense(Request $request): Response
    {
        return Response::ok($this->app->expenses->expense((int) $request->params['id']));
    }

    public function store(Request $request): Response
    {
        $expense = $this->app->expenses->create($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'EXPENSES', 'expenses', $expense['id'],
            ['amount' => $expense['amount'], 'category' => $expense['categoryCode']], 'INFO', $request->ip,
            $expense['expenseNumber']);

        return Response::created($expense);
    }

    public function approve(Request $request): Response
    {
        $expense = $this->app->expenses->approve((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'APPROVE', 'EXPENSES', 'expenses', $expense['id'],
            ['amount' => $expense['amount']], 'INFO', $request->ip, $expense['expenseNumber']);

        return Response::ok($expense);
    }

    public function pay(Request $request): Response
    {
        $expense = $this->app->expenses->pay(
            (int) $request->params['id'],
            (int) ($request->body['cashAccountId'] ?? 0),
            (int) $request->user['id']
        );
        $this->app->audit->log($request->user, 'PAY', 'EXPENSES', 'expenses', $expense['id'],
            ['amount' => $expense['totalAmount']], 'WARNING', $request->ip, $expense['expenseNumber']);

        return Response::ok($expense);
    }

    public function cancel(Request $request): Response
    {
        $expense = $this->app->expenses->cancel((int) $request->params['id']);
        $this->app->audit->log($request->user, 'CANCEL', 'EXPENSES', 'expenses', $expense['id'],
            ['amount' => $expense['amount']], 'WARNING', $request->ip, $expense['expenseNumber']);

        return Response::ok($expense);
    }

    public function summary(Request $request): Response
    {
        $from = $request->queryString('from');
        $to = $request->queryString('to');

        return Response::ok($this->app->expenses->summary($from !== '' ? $from : null, $to !== '' ? $to : null));
    }
}
