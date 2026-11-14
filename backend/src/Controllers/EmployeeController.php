<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Staff, attendance, leave and the monthly payroll, which settles through the expenses of the month.
 */
final class EmployeeController
{
    public function __construct(private readonly App $app)
    {
    }

    public function departments(Request $request): Response
    {
        return Response::ok($this->app->employees->departments());
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $activeOnly = $request->queryString('activeOnly');
        $result = $this->app->employees->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryInt('departmentId', 0),
            $activeOnly === '' ? null : in_array($activeOnly, ['1', 'true', 'yes'], true),
            $request->queryString('sort', 'lastName'),
            $request->queryString('direction', 'ASC')
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
            'summary' => $result['summary'],
        ]);
    }

    public function show(Request $request): Response
    {
        return Response::ok($this->app->employees->employee((int) $request->params['id']));
    }

    public function store(Request $request): Response
    {
        $employee = $this->app->employees->create($request->body, (int) $request->user['id']);
        $this->audit($request, 'CREATE', (int) $employee['id'], $employee['employeeNumber'],
            ['baseSalary' => $employee['baseSalary']]);

        return Response::created($employee);
    }

    public function update(Request $request): Response
    {
        $employee = $this->app->employees->update((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->audit($request, 'UPDATE', (int) $employee['id'], $employee['employeeNumber'], []);

        return Response::ok($employee);
    }

    public function destroy(Request $request): Response
    {
        $this->app->employees->deactivate((int) $request->params['id'], (int) $request->user['id']);
        $this->audit($request, 'DELETE', (int) $request->params['id'], 'employee', []);

        return Response::ok(['id' => (int) $request->params['id'], 'isActive' => false]);
    }

    public function attendance(Request $request): Response
    {
        $from = $request->queryString('from', date('Y-m-01'));
        $to = $request->queryString('to', date('Y-m-t'));

        return Response::ok($this->app->employees->attendance((int) $request->params['id'], $from, $to));
    }

    public function recordAttendance(Request $request): Response
    {
        $row = $this->app->employees->recordAttendance((int) $request->params['id'], $request->body);

        return Response::created($row);
    }

    public function leaveRequests(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->employees->leaveRequests($page, $pageSize, $request->queryString('status'),
            $request->queryInt('employeeId', 0));

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function requestLeave(Request $request): Response
    {
        $leave = $this->app->employees->requestLeave($request->body);
        $this->audit($request, 'CREATE', (int) $leave['id'], 'leave', ['type' => $leave['leaveType']]);

        return Response::created($leave);
    }

    public function approveLeave(Request $request): Response
    {
        return $this->decideLeave($request, 'approve');
    }

    public function rejectLeave(Request $request): Response
    {
        return $this->decideLeave($request, 'reject');
    }

    public function cancelLeave(Request $request): Response
    {
        return $this->decideLeave($request, 'cancel');
    }

    private function decideLeave(Request $request, string $decision): Response
    {
        $leave = $this->app->employees->decideLeave(
            (int) $request->params['id'],
            $decision,
            (int) $request->user['id'],
            isset($request->body['reason']) ? (string) $request->body['reason'] : null
        );
        $this->audit($request, strtoupper($decision), (int) $leave['id'], 'leave',
            ['status' => $leave['status']]);

        return Response::ok($leave);
    }

    public function salaryPayments(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 50)));
        $result = $this->app->employees->salaryPayments($page, $pageSize, $request->queryString('period'),
            in_array($request->queryString('unpaidOnly'), ['1', 'true', 'yes'], true));

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
            'summary' => $result['summary'],
        ]);
    }

    public function runPayroll(Request $request): Response
    {
        $result = $this->app->employees->runPayroll($request->body, (int) $request->user['id']);
        $this->audit($request, 'CREATE', 0, 'payroll ' . $result['period'],
            ['created' => $result['created']]);

        return Response::created($result);
    }

    public function paySalary(Request $request): Response
    {
        $cashAccountId = (int) ($request->body['cashAccountId'] ?? 0);
        $payment = $this->app->employees->paySalary((int) $request->params['id'], $cashAccountId,
            (int) $request->user['id']);
        $this->audit($request, 'PAY', (int) $payment['id'], 'salary ' . $payment['period'],
            ['expenseId' => $payment['expenseId'], 'net' => $payment['netAmount']], 'WARNING');

        return Response::ok($payment);
    }

    /** @param array<string,mixed> $payload */
    private function audit(Request $request, string $action, int $entityId, string $reference,
                           array $payload, string $severity = 'INFO'): void
    {
        $this->app->audit->log($request->user, $action, 'EMPLOYEES', 'employees', $entityId, $payload,
            $severity, $request->ip, $reference);
    }
}
