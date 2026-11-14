<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Staff, the daily attendance sheet, leave requests and the monthly payroll run.
 *
 * Payroll never invents money: a paid salary creates a PAID expense in the salary category, and the
 * payment of that expense writes the cash-book line, so the treasury stays the single source of
 * truth and a closed month refuses the run the same way it refuses any other posting.
 */
final class EmployeeRepository
{
    private const SORTABLE = ['employeeNumber', 'lastName', 'department', 'hireDate', 'baseSalary', 'isActive'];

    private const ATTENDANCE_STATUSES = ['PRESENT', 'ABSENT', 'LEAVE', 'SICK', 'HOLIDAY', 'SITE'];

    private const LEAVE_TYPES = ['ANNUAL', 'SICK', 'MATERNITY', 'UNPAID', 'EXCEPTIONAL'];

    public function __construct(private readonly Database $db, private readonly FinanceRepository $finance,
                                private readonly ExpenseRepository $expenses)
    {
    }

    /** @return array<int,array<string,mixed>> */
    public function departments(): array
    {
        return $this->db->select(
            'SELECT d.id, d.code, d.nameFr, d.nameAr, d.nameEn, d.isActive,
                    (SELECT COUNT(*) FROM employees e WHERE e.departmentId = d.id AND e.deletedAt IS NULL)
                        AS employeeCount
             FROM departments d WHERE d.deletedAt IS NULL ORDER BY d.nameFr'
        );
    }

    /** @return array{rows:array<int,array>,total:int,summary:array<string,mixed>} */
    public function paginate(int $page, int $pageSize, string $search, ?int $departmentId, ?bool $activeOnly,
                             string $sort, string $direction): array
    {
        $sort = in_array($sort, self::SORTABLE, true) ? $sort : 'lastName';
        $direction = strtoupper($direction) === 'DESC' ? 'DESC' : 'ASC';
        $sortColumn = $sort === 'department' ? 'd.nameFr' : 'e.' . $sort;

        $where = ['e.deletedAt IS NULL'];
        $params = [];
        if ($search !== '') {
            $where[] = '(e.employeeNumber LIKE :s1 OR e.firstName LIKE :s2 OR e.lastName LIKE :s3
                         OR e.position LIKE :s4 OR e.phone LIKE :s5)';
            foreach (['s1', 's2', 's3', 's4', 's5'] as $key) {
                $params[$key] = '%' . $search . '%';
            }
        }
        if ($departmentId !== null && $departmentId > 0) {
            $where[] = 'e.departmentId = :departmentId';
            $params['departmentId'] = $departmentId;
        }
        if ($activeOnly === true) {
            $where[] = 'e.isActive = 1';
        } elseif ($activeOnly === false) {
            $where[] = 'e.isActive = 0';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM employees e LEFT JOIN departments d ON d.id = e.departmentId ' . $clause,
            $params
        );

        $rows = $this->db->select(
            'SELECT e.id, e.employeeNumber, e.firstName, e.lastName, e.departmentId, d.nameFr AS departmentName,
                    e.position, e.phone, e.email, e.hireDate, e.contractType, e.baseSalary, e.isActive,
                    (SELECT COUNT(*) FROM salaryPayments s WHERE s.employeeId = e.id) AS paymentCount,
                    (SELECT MAX(s.period) FROM salaryPayments s WHERE s.employeeId = e.id) AS lastPaidPeriod
             FROM employees e LEFT JOIN departments d ON d.id = e.departmentId ' . $clause . '
             ORDER BY ' . $sortColumn . ' ' . $direction . '
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        $summary = $this->db->first(
            'SELECT COUNT(*) AS total,
                    SUM(CASE WHEN e.isActive = 1 THEN 1 ELSE 0 END) AS active,
                    COALESCE(SUM(CASE WHEN e.isActive = 1 THEN e.baseSalary ELSE 0 END), 0) AS monthlyPayroll
             FROM employees e ' . $clause,
            $params
        ) ?? [];

        return [
            'rows' => array_map([$this, 'hydrateEmployee'], $rows),
            'total' => $total,
            'summary' => [
                'total' => (int) ($summary['total'] ?? 0),
                'active' => (int) ($summary['active'] ?? 0),
                'monthlyPayroll' => round((float) ($summary['monthlyPayroll'] ?? 0), 2),
            ],
        ];
    }

    /** @return array<string,mixed> */
    public function employee(int $id): array
    {
        $row = $this->db->first(
            'SELECT e.*, d.nameFr AS departmentName,
                    (SELECT COUNT(*) FROM attendance a WHERE a.employeeId = e.id) AS attendanceCount,
                    (SELECT COUNT(*) FROM leaveRequests l WHERE l.employeeId = e.id AND l.status = \'APPROVED\')
                        AS approvedLeaveCount
             FROM employees e LEFT JOIN departments d ON d.id = e.departmentId
             WHERE e.id = :id AND e.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Employee');
        }
        $employee = $this->hydrateEmployee($row);
        $employee['salaryHistory'] = $this->db->select(
            'SELECT id, period, baseAmount, bonus, deductions, advance, netAmount, paidAt, expenseId
             FROM salaryPayments WHERE employeeId = :id ORDER BY period DESC LIMIT 24',
            ['id' => $id]
        );
        $employee['leaveRequests'] = $this->db->select(
            'SELECT id, leaveType, startDate, endDate, status, reason FROM leaveRequests
             WHERE employeeId = :id ORDER BY startDate DESC LIMIT 20',
            ['id' => $id]
        );

        return $employee;
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function create(array $data, int $userId): array
    {
        $this->assertEmployeePayload($data);
        $next = ((int) $this->db->scalar('SELECT COUNT(*) FROM employees')) + 1;
        $number = sprintf('EMP-%04d', $next);
        while ($this->db->scalar('SELECT id FROM employees WHERE employeeNumber = :n', ['n' => $number]) !== null) {
            $number = sprintf('EMP-%04d', ++$next);
        }

        $id = $this->db->insert(
            'INSERT INTO employees (employeeNumber, firstName, lastName, departmentId, position, phone, email,
                                   address, hireDate, contractType, baseSalary, userId, isActive, createdBy)
             VALUES (:number, :firstName, :lastName, :departmentId, :position, :phone, :email, :address,
                     :hireDate, :contractType, :baseSalary, :userId, 1, :createdBy)',
            [
                'number' => $number,
                'firstName' => trim((string) $data['firstName']),
                'lastName' => trim((string) $data['lastName']),
                'departmentId' => $this->nullableId($data, 'departmentId'),
                'position' => trim((string) ($data['position'] ?? '')),
                'phone' => trim((string) ($data['phone'] ?? '')),
                'email' => trim((string) ($data['email'] ?? '')),
                'address' => trim((string) ($data['address'] ?? '')),
                'hireDate' => $this->date($data['hireDate'] ?? null, 'hireDate'),
                'contractType' => $this->contractType($data['contractType'] ?? 'CDI'),
                'baseSalary' => $this->money($data['baseSalary'] ?? 0, 'baseSalary', true),
                'userId' => $this->nullableId($data, 'userId'),
                'createdBy' => $userId,
            ]
        );

        return $this->employee($id);
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function update(int $id, array $data, int $userId): array
    {
        $this->employee($id);
        $fields = [];
        $params = ['id' => $id, 'updatedBy' => $userId];

        foreach (['firstName', 'lastName', 'position', 'phone', 'email', 'address'] as $key) {
            if (array_key_exists($key, $data)) {
                $fields[] = $key . ' = :' . $key;
                $params[$key] = trim((string) $data[$key]);
            }
        }
        if (array_key_exists('departmentId', $data)) {
            $fields[] = 'departmentId = :departmentId';
            $params['departmentId'] = $this->nullableId($data, 'departmentId');
        }
        if (array_key_exists('contractType', $data)) {
            $fields[] = 'contractType = :contractType';
            $params['contractType'] = $this->contractType($data['contractType']);
        }
        if (array_key_exists('baseSalary', $data)) {
            $fields[] = 'baseSalary = :baseSalary';
            $params['baseSalary'] = $this->money($data['baseSalary'], 'baseSalary', true);
        }
        if (array_key_exists('hireDate', $data)) {
            $fields[] = 'hireDate = :hireDate';
            $params['hireDate'] = $this->date($data['hireDate'], 'hireDate');
        }
        if (array_key_exists('isActive', $data)) {
            $fields[] = 'isActive = :isActive';
            $params['isActive'] = !empty($data['isActive']) ? 1 : 0;
        }
        if ($fields === []) {
            return $this->employee($id);
        }

        $this->db->execute(
            'UPDATE employees SET ' . implode(', ', $fields) . ', updatedBy = :updatedBy WHERE id = :id',
            $params
        );

        return $this->employee($id);
    }

    public function deactivate(int $id, int $userId): void
    {
        $this->employee($id);
        $this->db->execute(
            'UPDATE employees SET isActive = 0, deletedAt = NOW(), updatedBy = :updatedBy WHERE id = :id',
            ['id' => $id, 'updatedBy' => $userId]
        );
    }

    /** @return array<int,array<string,mixed>> */
    public function attendance(int $employeeId, string $from, string $to): array
    {
        $this->employee($employeeId);

        return $this->db->select(
            'SELECT id, employeeId, workDate, checkIn, checkOut, status, notes FROM attendance
             WHERE employeeId = :id AND workDate BETWEEN :from AND :to ORDER BY workDate DESC',
            ['id' => $employeeId, 'from' => $from, 'to' => $to]
        );
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function recordAttendance(int $employeeId, array $data): array
    {
        $this->employee($employeeId);
        $status = strtoupper((string) ($data['status'] ?? 'PRESENT'));
        if (!in_array($status, self::ATTENDANCE_STATUSES, true)) {
            throw ApiException::validation(['status' => 'validation.in']);
        }
        $workDate = $this->date($data['workDate'] ?? null, 'workDate');
        $checkIn = $this->time($data['checkIn'] ?? null);
        $checkOut = $this->time($data['checkOut'] ?? null);

        $this->db->execute(
            'INSERT INTO attendance (employeeId, workDate, checkIn, checkOut, status, notes)
             VALUES (:employeeId, :workDate, :checkIn, :checkOut, :status, :notes)
             ON DUPLICATE KEY UPDATE checkIn = VALUES(checkIn), checkOut = VALUES(checkOut),
                                     status = VALUES(status), notes = VALUES(notes)',
            [
                'employeeId' => $employeeId,
                'workDate' => $workDate,
                'checkIn' => $checkIn,
                'checkOut' => $checkOut,
                'status' => $status,
                'notes' => trim((string) ($data['notes'] ?? '')),
            ]
        );

        return $this->db->first(
            'SELECT id, employeeId, workDate, checkIn, checkOut, status, notes FROM attendance
             WHERE employeeId = :id AND workDate = :workDate',
            ['id' => $employeeId, 'workDate' => $workDate]
        ) ?? [];
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function leaveRequests(int $page, int $pageSize, string $status, ?int $employeeId): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($status !== '') {
            $where[] = 'l.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($employeeId !== null && $employeeId > 0) {
            $where[] = 'l.employeeId = :employeeId';
            $params['employeeId'] = $employeeId;
        }
        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM leaveRequests l ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT l.id, l.employeeId, CONCAT(e.employeeNumber, \' - \', e.lastName, \' \', e.firstName) AS employee,
                    l.leaveType, l.startDate, l.endDate, DATEDIFF(l.endDate, l.startDate) + 1 AS days,
                    l.status, l.reason, l.approvedAt, u.fullName AS approvedByName
             FROM leaveRequests l
             JOIN employees e ON e.id = l.employeeId
             LEFT JOIN users u ON u.id = l.approvedBy ' . $clause . '
             ORDER BY l.startDate DESC LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => $rows, 'total' => $total];
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function requestLeave(array $data): array
    {
        $employeeId = (int) ($data['employeeId'] ?? 0);
        $this->employee($employeeId);
        $type = strtoupper((string) ($data['leaveType'] ?? 'ANNUAL'));
        if (!in_array($type, self::LEAVE_TYPES, true)) {
            throw ApiException::validation(['leaveType' => 'validation.in']);
        }
        $start = $this->date($data['startDate'] ?? null, 'startDate');
        $end = $this->date($data['endDate'] ?? null, 'endDate');
        if ($end < $start) {
            throw ApiException::validation(['endDate' => 'validation.after_or_equal']);
        }

        $id = $this->db->insert(
            'INSERT INTO leaveRequests (employeeId, leaveType, startDate, endDate, status, reason)
             VALUES (:employeeId, :leaveType, :startDate, :endDate, \'REQUESTED\', :reason)',
            [
                'employeeId' => $employeeId,
                'leaveType' => $type,
                'startDate' => $start,
                'endDate' => $end,
                'reason' => trim((string) ($data['reason'] ?? '')),
            ]
        );

        return $this->leaveRequest($id);
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function decideLeave(int $id, string $decision, int $userId, ?string $reason): array
    {
        $current = $this->leaveRequest($id);
        if ($current['status'] !== 'REQUESTED') {
            throw ApiException::conflict('INVALID_STATUS', 'Only a pending request can be decided.');
        }
        $status = $decision === 'approve' ? 'APPROVED' : ($decision === 'reject' ? 'REJECTED' : 'CANCELLED');

        $note = $reason === null || trim($reason) === '' ? (string) $current['reason'] : trim($reason);
        $this->db->execute(
            'UPDATE leaveRequests SET status = :status, approvedBy = :approvedBy, approvedAt = NOW(),
                    reason = :reason WHERE id = :id',
            ['status' => $status, 'approvedBy' => $userId, 'reason' => $note, 'id' => $id]
        );

        return $this->leaveRequest($id);
    }

    /** @return array<string,mixed> */
    private function leaveRequest(int $id): array
    {
        $row = $this->db->first(
            'SELECT l.id, l.employeeId, CONCAT(e.employeeNumber, \' - \', e.lastName, \' \', e.firstName) AS employee,
                    l.leaveType, l.startDate, l.endDate, DATEDIFF(l.endDate, l.startDate) + 1 AS days,
                    l.status, l.reason, l.approvedAt, u.fullName AS approvedByName
             FROM leaveRequests l
             JOIN employees e ON e.id = l.employeeId
             LEFT JOIN users u ON u.id = l.approvedBy
             WHERE l.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Leave request');
        }

        return $row;
    }

    /** @return array{rows:array<int,array>,total:int,summary:array<string,mixed>} */
    public function salaryPayments(int $page, int $pageSize, string $period, bool $onlyUnpaid): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($period !== '') {
            $where[] = 's.period = :period';
            $params['period'] = $period;
        }
        if ($onlyUnpaid) {
            $where[] = 's.paidAt IS NULL';
        }
        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM salaryPayments s ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT s.id, s.employeeId, e.employeeNumber, CONCAT(e.lastName, \' \', e.firstName) AS employee,
                    d.nameFr AS departmentName, s.period, s.baseAmount, s.bonus, s.deductions, s.advance,
                    s.netAmount, s.paidAt, s.expenseId, exp.expenseNumber
             FROM salaryPayments s
             JOIN employees e ON e.id = s.employeeId
             LEFT JOIN departments d ON d.id = e.departmentId
             LEFT JOIN expenses exp ON exp.id = s.expenseId ' . $clause . '
             ORDER BY s.period DESC, e.lastName LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        $summaryRow = $this->db->first(
            'SELECT COALESCE(SUM(s.netAmount), 0) AS net,
                    COALESCE(SUM(CASE WHEN s.paidAt IS NOT NULL THEN s.netAmount ELSE 0 END), 0) AS paid,
                    COALESCE(SUM(CASE WHEN s.paidAt IS NULL THEN s.netAmount ELSE 0 END), 0) AS pending,
                    COUNT(*) AS lines
             FROM salaryPayments s ' . $clause,
            $params
        ) ?? [];

        return [
            'rows' => array_map(static function (array $row): array {
                $row['netAmount'] = round((float) $row['netAmount'], 2);
                $row['baseAmount'] = round((float) $row['baseAmount'], 2);
                $row['isPaid'] = $row['paidAt'] !== null;

                return $row;
            }, $rows),
            'total' => $total,
            'summary' => [
                'period' => $period,
                'net' => round((float) ($summaryRow['net'] ?? 0), 2),
                'paid' => round((float) ($summaryRow['paid'] ?? 0), 2),
                'pending' => round((float) ($summaryRow['pending'] ?? 0), 2),
                'lines' => (int) ($summaryRow['lines'] ?? 0),
            ],
        ];
    }

    /**
     * Generates the payroll lines of a month for every active employee that has none yet.
     *
     * @param array<string,mixed> $data
     * @return array{period:string,created:int,lines:array<int,array<string,mixed>>}
     */
    public function runPayroll(array $data, int $userId): array
    {
        $period = (string) ($data['period'] ?? date('Y-m'));
        if (preg_match('/^(\d{4})-(\d{2})$/', $period, $matches) !== 1) {
            throw ApiException::validation(['period' => 'validation.format']);
        }
        // The month must exist in the accounting calendar and still be open: a payroll run is a
        // commitment, so it is refused for a month that was closed by the accountant.
        $calendar = $this->db->first('SELECT id, status FROM accountingPeriods WHERE code = :code',
            ['code' => $period]);
        if ($calendar === null) {
            throw ApiException::validation(['period' => 'validation.exists']);
        }
        if ($calendar['status'] !== 'OPEN') {
            throw ApiException::conflict('PERIOD_CLOSED', 'The period ' . $period . ' is closed.');
        }

        $bonus = $this->money($data['bonus'] ?? 0, 'bonus', false);
        $deductions = $this->money($data['deductions'] ?? 0, 'deductions', false);
        $employees = $this->db->select(
            'SELECT e.id, e.baseSalary FROM employees e
             WHERE e.deletedAt IS NULL AND e.isActive = 1
               AND NOT EXISTS (SELECT 1 FROM salaryPayments s WHERE s.employeeId = e.id AND s.period = :period)
             ORDER BY e.lastName',
            ['period' => $period]
        );

        $created = 0;
        foreach ($employees as $employee) {
            $this->db->execute(
                'INSERT INTO salaryPayments (employeeId, period, baseAmount, bonus, deductions, advance, userId)
                 VALUES (:employeeId, :period, :baseAmount, :bonus, :deductions, 0, :userId)',
                [
                    'employeeId' => (int) $employee['id'],
                    'period' => $period,
                    'baseAmount' => round((float) $employee['baseSalary'], 2),
                    'bonus' => $bonus,
                    'deductions' => $deductions,
                    'userId' => $userId,
                ]
            );
            $created++;
        }

        $result = $this->salaryPayments(1, 200, $period, false);

        return ['period' => $period, 'created' => $created, 'lines' => $result['rows']];
    }

    /**
     * Pays one salary line: a PAID expense in the salary category, then the cash-book line through
     * the expense payment, inside one transaction so a closed month rolls everything back.
     *
     * @return array<string,mixed>
     */
    public function paySalary(int $id, int $cashAccountId, int $userId): array
    {
        $row = $this->db->first(
            'SELECT s.*, e.employeeNumber, CONCAT(e.lastName, \' \', e.firstName) AS employeeName
             FROM salaryPayments s JOIN employees e ON e.id = s.employeeId WHERE s.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Salary payment');
        }
        if ($row['paidAt'] !== null) {
            throw ApiException::conflict('INVALID_STATUS', 'This salary has already been paid.');
        }
        $net = round((float) $row['netAmount'], 2);
        if ($net <= 0) {
            throw ApiException::conflict('INVALID_STATUS', 'A salary with no net amount cannot be paid.');
        }

        $categoryId = (int) $this->db->scalar(
            'SELECT id FROM expenseCategories WHERE isSalary = 1 AND isActive = 1 ORDER BY id LIMIT 1'
        );
        if ($categoryId === 0) {
            throw ApiException::conflict('INVALID_STATUS', 'No salary expense category is configured.');
        }

        $expenseId = $this->db->transaction(function () use ($row, $net, $cashAccountId, $userId, $categoryId): int {
            $draft = $this->expenses->create([
                'categoryId' => $categoryId,
                'label' => 'Salaire ' . $row['period'] . ' - ' . $row['employeeName'],
                'amount' => $net,
                'vatAmount' => 0,
                'expenseDate' => date('Y-m-d', strtotime((string) $row['period'] . '-28')),
                'paymentMethod' => 'TRANSFER',
                'cashAccountId' => $cashAccountId,
                'employeeId' => (int) $row['employeeId'],
                'notes' => 'Paie ' . $row['period'],
            ], $userId);
            $paid = $this->expenses->pay((int) $draft['id'], $cashAccountId, $userId);
            $this->db->execute(
                'UPDATE salaryPayments SET paidAt = NOW(), expenseId = :expenseId, userId = :userId WHERE id = :id',
                ['expenseId' => (int) $paid['id'], 'userId' => $userId, 'id' => (int) $row['id']]
            );

            return (int) $paid['id'];
        });

        return [
            'id' => (int) $row['id'],
            'employeeId' => (int) $row['employeeId'],
            'period' => (string) $row['period'],
            'netAmount' => $net,
            'expenseId' => $expenseId,
            'paidAt' => date('Y-m-d H:i:s'),
        ];
    }

    /** @return array<string,mixed> */
    private function hydrateEmployee(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'employeeNumber' => (string) $row['employeeNumber'],
            'firstName' => (string) $row['firstName'],
            'lastName' => (string) $row['lastName'],
            'fullName' => trim($row['lastName'] . ' ' . $row['firstName']),
            'departmentId' => $row['departmentId'] !== null ? (int) $row['departmentId'] : null,
            'departmentName' => $row['departmentName'] ?? null,
            'position' => (string) ($row['position'] ?? ''),
            'phone' => (string) ($row['phone'] ?? ''),
            'email' => (string) ($row['email'] ?? ''),
            'address' => (string) ($row['address'] ?? ''),
            'hireDate' => (string) $row['hireDate'],
            'contractType' => (string) $row['contractType'],
            'baseSalary' => round((float) $row['baseSalary'], 2),
            'isActive' => (bool) $row['isActive'],
            'paymentCount' => isset($row['paymentCount']) ? (int) $row['paymentCount'] : 0,
            'lastPaidPeriod' => $row['lastPaidPeriod'] ?? null,
        ];
    }

    /** @param array<string,mixed> $data */
    private function assertEmployeePayload(array $data): void
    {
        $errors = [];
        if (trim((string) ($data['firstName'] ?? '')) === '') {
            $errors['firstName'] = 'validation.required';
        }
        if (trim((string) ($data['lastName'] ?? '')) === '') {
            $errors['lastName'] = 'validation.required';
        }
        if (($data['hireDate'] ?? null) === null) {
            $errors['hireDate'] = 'validation.required';
        }
        if ($errors !== []) {
            throw ApiException::validation($errors);
        }
    }

    /** @param array<string,mixed> $data */
    private function nullableId(array $data, string $key): ?int
    {
        if (!isset($data[$key]) || $data[$key] === '' || (int) $data[$key] === 0) {
            return null;
        }
        $id = (int) $data[$key];
        $table = $key === 'departmentId' ? 'departments' : 'users';
        if ($this->db->scalar('SELECT id FROM ' . $table . ' WHERE id = :id', ['id' => $id]) === null) {
            throw ApiException::validation([$key => 'validation.exists']);
        }

        return $id;
    }

    private function contractType(mixed $value): string
    {
        $type = strtoupper((string) $value);
        if (!in_array($type, ['CDI', 'CDD', 'INTERIM', 'STAGE'], true)) {
            throw ApiException::validation(['contractType' => 'validation.in']);
        }

        return $type;
    }

    private function date(mixed $value, string $field): string
    {
        $text = is_string($value) ? trim($value) : '';
        $parsed = $text === '' ? false : date_create($text);
        if ($parsed === false) {
            throw ApiException::validation([$field => 'validation.date']);
        }

        return $parsed->format('Y-m-d');
    }

    private function time(mixed $value): ?string
    {
        $text = is_string($value) ? trim($value) : '';
        if ($text === '') {
            return null;
        }
        $parsed = date_create($text);
        if ($parsed === false) {
            throw ApiException::validation(['checkIn' => 'validation.date']);
        }

        return $parsed->format('H:i:s');
    }

    private function money(mixed $value, string $field, bool $positive): float
    {
        if (!is_numeric($value)) {
            throw ApiException::validation([$field => 'validation.numeric']);
        }
        $amount = round((float) $value, 2);
        if ($positive ? $amount <= 0 : $amount < 0) {
            throw ApiException::validation([$field => 'validation.min']);
        }

        return $amount;
    }
}
