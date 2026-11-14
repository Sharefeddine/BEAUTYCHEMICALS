<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * The cash book and the accounting calendar.
 *
 * <p>Every movement of money in the application lands here: a customer settlement, a supplier
 * payment, an expense, a salary. One row says where the money came from, and the accounting period
 * it belongs to decides whether it may be written at all — a closed month refuses new postings, which
 * is the single rule this class exists to enforce. Amounts are never recomputed: the book sums what
 * the source documents wrote.</p>
 */
final class FinanceRepository
{
    private const DIRECTIONS = ['IN', 'OUT'];
    private const SOURCE_TYPES = ['INVOICE', 'PAYMENT', 'SUPPLIERPAYMENT', 'EXPENSE', 'SALARY', 'TRANSFER', 'OTHER'];

    public function __construct(private readonly Database $db)
    {
    }

    // ------------------------------------------------------------------ periods

    /** @return array<int,array> */
    public function periods(): array
    {
        $rows = $this->db->select(
            'SELECT p.*, u.fullName AS closedByName,
                    (SELECT COALESCE(SUM(t.amount), 0) FROM cashTransactions t
                        WHERE t.accountingPeriodId = p.id AND t.direction = \'IN\') AS totalIn,
                    (SELECT COALESCE(SUM(t.amount), 0) FROM cashTransactions t
                        WHERE t.accountingPeriodId = p.id AND t.direction = \'OUT\') AS totalOut,
                    (SELECT COUNT(*) FROM cashTransactions t WHERE t.accountingPeriodId = p.id) AS entryCount
             FROM accountingPeriods p
             LEFT JOIN users u ON u.id = p.closedBy
             ORDER BY p.code DESC'
        );

        return array_map(fn (array $row): array => $this->hydratePeriod($row), $rows);
    }

    /** @return array<string,mixed> */
    public function period(int $id): array
    {
        $row = $this->db->first(
            'SELECT p.*, u.fullName AS closedByName,
                    (SELECT COALESCE(SUM(t.amount), 0) FROM cashTransactions t
                        WHERE t.accountingPeriodId = p.id AND t.direction = \'IN\') AS totalIn,
                    (SELECT COALESCE(SUM(t.amount), 0) FROM cashTransactions t
                        WHERE t.accountingPeriodId = p.id AND t.direction = \'OUT\') AS totalOut,
                    (SELECT COUNT(*) FROM cashTransactions t WHERE t.accountingPeriodId = p.id) AS entryCount
             FROM accountingPeriods p
             LEFT JOIN users u ON u.id = p.closedBy
             WHERE p.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Accounting period');
        }

        return $this->hydratePeriod($row);
    }

    /** The open or closed period a date falls into, creating it when the calendar has no row yet. */
    public function periodForDate(string $date): array
    {
        $code = substr($date, 0, 7);
        $row = $this->db->first('SELECT id FROM accountingPeriods WHERE code = :code', ['code' => $code]);
        if ($row === null) {
            $start = new \DateTimeImmutable($code . '-01');
            $this->db->execute(
                'INSERT INTO accountingPeriods (code, startDate, endDate, status) VALUES (:code, :start, :end, \'OPEN\')',
                [
                    'code' => $code,
                    'start' => $start->format('Y-m-d'),
                    'end' => $start->format('Y-m-t'),
                ]
            );

            return $this->period($this->db->lastId());
        }

        return $this->period((int) $row['id']);
    }

    /** @return array<string,mixed> */
    public function closePeriod(int $id, int $userId): array
    {
        $period = $this->period($id);
        if ($period['status'] === 'CLOSED') {
            throw ApiException::conflict('This accounting period is already closed.', 'PERIOD_ALREADY_CLOSED',
                ['code' => $period['code']]);
        }
        $this->db->execute(
            'UPDATE accountingPeriods SET status = \'CLOSED\', closedBy = :userId, closedAt = NOW() WHERE id = :id',
            ['userId' => $userId, 'id' => $id]
        );

        return $this->period($id);
    }

    /**
     * Reopening is deliberately possible but never silent: the audit log records who did it, and a
     * global super-admin rule is the only thing an installation can add on top.
     *
     * @return array<string,mixed>
     */
    public function reopenPeriod(int $id): array
    {
        $period = $this->period($id);
        if ($period['status'] === 'OPEN') {
            throw ApiException::conflict('This accounting period is already open.', 'INVALID_STATUS',
                ['status' => $period['status']]);
        }
        $this->db->execute(
            'UPDATE accountingPeriods SET status = \'OPEN\', closedBy = NULL, closedAt = NULL WHERE id = :id',
            ['id' => $id]
        );

        return $this->period($id);
    }

    // ----------------------------------------------------------------- cash book

    /** @return array{rows:array<int,array>,total:int,totals:array<string,float>} */
    public function paginateCashBook(int $page, int $pageSize, string $search, ?int $cashAccountId,
                                     ?string $direction, ?string $from, ?string $to, ?string $sourceType): array
    {
        $where = ['1 = 1'];
        $params = [];

        if ($search !== '') {
            $where[] = '(t.description LIKE :search OR a.name LIKE :searchAccount OR t.sourceType LIKE :searchSource)';
            $term = '%' . $search . '%';
            $params['search'] = $term;
            $params['searchAccount'] = $term;
            $params['searchSource'] = $term;
        }
        if ($cashAccountId !== null && $cashAccountId > 0) {
            $where[] = 't.cashAccountId = :cashAccountId';
            $params['cashAccountId'] = $cashAccountId;
        }
        if ($direction !== null && in_array(strtoupper($direction), self::DIRECTIONS, true)) {
            $where[] = 't.direction = :direction';
            $params['direction'] = strtoupper($direction);
        }
        if ($sourceType !== null && in_array(strtoupper($sourceType), self::SOURCE_TYPES, true)) {
            $where[] = 't.sourceType = :sourceType';
            $params['sourceType'] = strtoupper($sourceType);
        }
        if ($from !== null && $from !== '') {
            $where[] = 't.occurredAt >= :from';
            $params['from'] = $from . ' 00:00:00';
        }
        if ($to !== null && $to !== '') {
            $where[] = 't.occurredAt <= :to';
            $params['to'] = $to . ' 23:59:59';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM cashTransactions t JOIN cashAccounts a ON a.id = t.cashAccountId ' . $clause,
            $params
        );
        $totals = $this->db->first(
            'SELECT COALESCE(SUM(CASE WHEN t.direction = \'IN\' THEN t.amount ELSE 0 END), 0) AS totalIn,
                    COALESCE(SUM(CASE WHEN t.direction = \'OUT\' THEN t.amount ELSE 0 END), 0) AS totalOut
             FROM cashTransactions t JOIN cashAccounts a ON a.id = t.cashAccountId ' . $clause,
            $params
        ) ?? ['totalIn' => 0, 'totalOut' => 0];

        $rows = $this->db->select(
            'SELECT t.*, a.code AS accountCode, a.name AS accountName, a.type AS accountType,
                    p.code AS periodCode, u.fullName AS userName
             FROM cashTransactions t
             JOIN cashAccounts a ON a.id = t.cashAccountId
             LEFT JOIN accountingPeriods p ON p.id = t.accountingPeriodId
             LEFT JOIN users u ON u.id = t.userId ' . $clause . '
             ORDER BY t.occurredAt DESC, t.id DESC
             LIMIT ' . $pageSize . ' OFFSET ' . max(0, ($page - 1) * $pageSize),
            $params
        );

        return [
            'rows' => array_map(fn (array $row): array => $this->hydrateTransaction($row), $rows),
            'total' => $total,
            'totals' => [
                'totalIn' => round((float) $totals['totalIn'], 2),
                'totalOut' => round((float) $totals['totalOut'], 2),
                'net' => round((float) $totals['totalIn'] - (float) $totals['totalOut'], 2),
            ],
        ];
    }

    /**
     * The treasury per account: what it started with, what came in, what went out, where it stands.
     *
     * @return array{accounts:array<int,array>,totals:array<string,float>}
     */
    public function treasury(?string $from = null, ?string $to = null): array
    {
        $join = '';
        $params = [];
        if ($from !== null && $from !== '') {
            $join .= ' AND t.occurredAt >= :from';
            $params['from'] = $from . ' 00:00:00';
        }
        if ($to !== null && $to !== '') {
            $join .= ' AND t.occurredAt <= :to';
            $params['to'] = $to . ' 23:59:59';
        }

        $rows = $this->db->select(
            'SELECT a.id, a.code, a.name, a.type, a.openingBalance,
                    COALESCE(SUM(CASE WHEN t.direction = \'IN\' THEN t.amount ELSE 0 END), 0) AS totalIn,
                    COALESCE(SUM(CASE WHEN t.direction = \'OUT\' THEN t.amount ELSE 0 END), 0) AS totalOut
             FROM cashAccounts a
             LEFT JOIN cashTransactions t ON t.cashAccountId = a.id' . $join . '
             GROUP BY a.id, a.code, a.name, a.type, a.openingBalance
             ORDER BY a.name',
            $params
        );

        $accounts = [];
        $totalIn = 0.0;
        $totalOut = 0.0;
        $opening = 0.0;
        foreach ($rows as $row) {
            $in = round((float) $row['totalIn'], 2);
            $out = round((float) $row['totalOut'], 2);
            $start = round((float) $row['openingBalance'], 2);
            $totalIn += $in;
            $totalOut += $out;
            $opening += $start;
            $accounts[] = [
                'id' => (int) $row['id'],
                'code' => (string) $row['code'],
                'name' => (string) $row['name'],
                'type' => (string) $row['type'],
                'openingBalance' => $start,
                'totalIn' => $in,
                'totalOut' => $out,
                'balance' => round($start + $in - $out, 2),
            ];
        }

        return [
            'accounts' => $accounts,
            'totals' => [
                'openingBalance' => round($opening, 2),
                'totalIn' => round($totalIn, 2),
                'totalOut' => round($totalOut, 2),
                'balance' => round($opening + $totalIn - $totalOut, 2),
            ],
        ];
    }

    /**
     * Income and expense of a window, broken down by where the money came from.
     *
     * @return array<string,float|int|array<int,array>>
     */
    public function summary(?string $from = null, ?string $to = null): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($from !== null && $from !== '') {
            $where[] = 't.occurredAt >= :from';
            $params['from'] = $from . ' 00:00:00';
        }
        if ($to !== null && $to !== '') {
            $where[] = 't.occurredAt <= :to';
            $params['to'] = $to . ' 23:59:59';
        }
        $clause = 'WHERE ' . implode(' AND ', $where);

        $rows = $this->db->select(
            'SELECT t.sourceType, t.direction, COALESCE(SUM(t.amount), 0) AS amount, COUNT(*) AS entryCount
             FROM cashTransactions t ' . $clause . '
             GROUP BY t.sourceType, t.direction
             ORDER BY t.sourceType',
            $params
        );

        $income = 0.0;
        $expense = 0.0;
        $bySource = [];
        foreach ($rows as $row) {
            $amount = round((float) $row['amount'], 2);
            $direction = (string) $row['direction'];
            if ($direction === 'IN') {
                $income += $amount;
            } else {
                $expense += $amount;
            }
            $bySource[] = [
                'sourceType' => (string) $row['sourceType'],
                'direction' => $direction,
                'amount' => $amount,
                'entryCount' => (int) $row['entryCount'],
            ];
        }

        $receivable = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(balance), 0) FROM customers WHERE deletedAt IS NULL AND balance > 0'
        );
        $payable = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(balance), 0) FROM suppliers WHERE deletedAt IS NULL AND balance > 0'
        );

        return [
            'income' => round($income, 2),
            'expense' => round($expense, 2),
            'result' => round($income - $expense, 2),
            'receivable' => round($receivable, 2),
            'payable' => round($payable, 2),
            'bySource' => $bySource,
        ];
    }

    // ---------------------------------------------------------------- postings

    /**
     * Writes one cash book row. Refuses a closed period, which is the whole point of the calendar:
     * opening the month is enough to keep the two books consistent.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function record(array $data): array
    {
        $cashAccountId = (int) ($data['cashAccountId'] ?? 0);
        if ($cashAccountId <= 0) {
            throw ApiException::validation(['cashAccountId' => 'validation.required']);
        }
        $account = $this->db->first('SELECT id, isActive FROM cashAccounts WHERE id = :id', ['id' => $cashAccountId]);
        if ($account === null) {
            throw ApiException::validation(['cashAccountId' => 'validation.exists']);
        }
        if ((int) $account['isActive'] !== 1) {
            throw ApiException::conflict('This cash account is closed.', 'CASH_ACCOUNT_INACTIVE',
                ['cashAccountId' => $cashAccountId]);
        }

        $amount = round((float) ($data['amount'] ?? 0), 2);
        if ($amount <= 0) {
            throw ApiException::validation(['amount' => 'validation.positive']);
        }
        $direction = strtoupper(trim((string) ($data['direction'] ?? 'IN')));
        if (!in_array($direction, self::DIRECTIONS, true)) {
            throw ApiException::validation(['direction' => 'validation.allowed_values']);
        }
        $sourceType = strtoupper(trim((string) ($data['sourceType'] ?? 'OTHER')));
        if (!in_array($sourceType, self::SOURCE_TYPES, true)) {
            throw ApiException::validation(['sourceType' => 'validation.allowed_values']);
        }

        $userId = (int) ($data['userId'] ?? 0);
        if ($userId <= 0) {
            throw ApiException::validation(['userId' => 'validation.required']);
        }

        $occurredAt = $this->dateTime($data['occurredAt'] ?? null) ?? date('Y-m-d H:i:s');
        $period = $this->periodForDate(substr($occurredAt, 0, 10));
        if ($period['status'] === 'CLOSED') {
            throw ApiException::conflict(
                'The accounting period ' . $period['code'] . ' is closed: nothing can be posted into it.',
                'PERIOD_CLOSED',
                ['period' => $period['code'], 'occurredAt' => $occurredAt]
            );
        }

        $this->db->execute(
            'INSERT INTO cashTransactions (cashAccountId, direction, amount, sourceType, sourceId, description,
                    occurredAt, accountingPeriodId, userId)
             VALUES (:cashAccountId, :direction, :amount, :sourceType, :sourceId, :description, :occurredAt,
                    :accountingPeriodId, :userId)',
            [
                'cashAccountId' => $cashAccountId,
                'direction' => $direction,
                'amount' => $amount,
                'sourceType' => $sourceType,
                'sourceId' => isset($data['sourceId']) && (int) $data['sourceId'] > 0 ? (int) $data['sourceId'] : null,
                'description' => mb_substr(trim((string) ($data['description'] ?? '')), 0, 255),
                'occurredAt' => $occurredAt,
                'accountingPeriodId' => (int) $period['id'],
                'userId' => $userId,
            ]
        );

        return $this->transaction($this->db->lastId());
    }

    /** @return array<string,mixed> */
    public function transaction(int $id): array
    {
        $row = $this->db->first(
            'SELECT t.*, a.code AS accountCode, a.name AS accountName, a.type AS accountType,
                    p.code AS periodCode, u.fullName AS userName
             FROM cashTransactions t
             JOIN cashAccounts a ON a.id = t.cashAccountId
             LEFT JOIN accountingPeriods p ON p.id = t.accountingPeriodId
             LEFT JOIN users u ON u.id = t.userId
             WHERE t.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Cash transaction');
        }

        return $this->hydrateTransaction($row);
    }

    // ------------------------------------------------------------------ mapping

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydratePeriod(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'startDate' => (string) $row['startDate'],
            'endDate' => (string) $row['endDate'],
            'status' => (string) $row['status'],
            'isOpen' => (string) $row['status'] === 'OPEN',
            'closedBy' => $row['closedBy'] === null ? null : (int) $row['closedBy'],
            'closedByName' => $row['closedByName'] ?? null,
            'closedAt' => $row['closedAt'],
            'totalIn' => round((float) ($row['totalIn'] ?? 0), 2),
            'totalOut' => round((float) ($row['totalOut'] ?? 0), 2),
            'entryCount' => (int) ($row['entryCount'] ?? 0),
        ];
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateTransaction(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'cashAccountId' => (int) $row['cashAccountId'],
            'accountCode' => (string) ($row['accountCode'] ?? ''),
            'accountName' => (string) ($row['accountName'] ?? ''),
            'accountType' => (string) ($row['accountType'] ?? ''),
            'direction' => (string) $row['direction'],
            'amount' => round((float) $row['amount'], 2),
            'signedAmount' => (string) $row['direction'] === 'IN'
                ? round((float) $row['amount'], 2) : -round((float) $row['amount'], 2),
            'sourceType' => (string) $row['sourceType'],
            'sourceId' => $row['sourceId'] === null ? null : (int) $row['sourceId'],
            'description' => (string) $row['description'],
            'occurredAt' => (string) $row['occurredAt'],
            'periodCode' => (string) ($row['periodCode'] ?? ''),
            'userName' => $row['userName'] ?? null,
        ];
    }

    private function dateTime(mixed $value): ?string
    {
        $raw = trim((string) ($value ?? ''));
        if ($raw === '') {
            return null;
        }
        $parsed = date_create($raw);

        return $parsed === false ? null : $parsed->format('Y-m-d H:i:s');
    }
}
