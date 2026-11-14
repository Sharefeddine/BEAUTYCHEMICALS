<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Operating expenses: what the company spends outside purchasing.
 *
 * <p>An expense is entered by whoever knows about it (`DRAFT`), approved by a manager
 * (`APPROVED`), and only then paid — and paying is the moment the cash book is written and the
 * accounting period may refuse it. A cancelled expense never touched the treasury; the row stays so
 * the paper trail does not have holes.</p>
 */
final class ExpenseRepository
{
    private const STATUSES = ['DRAFT', 'APPROVED', 'PAID', 'CANCELLED'];
    private const METHODS = ['CASH', 'CHEQUE', 'TRANSFER', 'CARD', 'CREDIT'];

    public function __construct(
        private readonly Database $db,
        private readonly FinanceRepository $finance
    ) {
    }

    /** @return array<int,array> */
    public function categories(): array
    {
        return array_map(
            static fn (array $row): array => [
                'id' => (int) $row['id'],
                'code' => (string) $row['code'],
                'nameFr' => (string) $row['nameFr'],
                'nameAr' => (string) $row['nameAr'],
                'nameEn' => (string) ($row['nameEn'] ?? ''),
                'isSalary' => (bool) $row['isSalary'],
                'isVehicle' => (bool) $row['isVehicle'],
            ],
            $this->db->select(
                'SELECT id, code, nameFr, nameAr, nameEn, isSalary, isVehicle FROM expenseCategories
                 WHERE isActive = 1 ORDER BY code'
            )
        );
    }

    /** @return array{rows:array<int,array>,total:int,summary:array<string,float>} */
    public function paginate(int $page, int $pageSize, string $search, ?int $categoryId, ?string $status,
                             ?string $from, ?string $to): array
    {
        $where = ['e.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(e.label LIKE :search OR e.expenseNumber LIKE :searchNumber OR c.nameFr LIKE :searchCategory)';
            $term = '%' . $search . '%';
            $params['search'] = $term;
            $params['searchNumber'] = $term;
            $params['searchCategory'] = $term;
        }
        if ($categoryId !== null && $categoryId > 0) {
            $where[] = 'e.categoryId = :categoryId';
            $params['categoryId'] = $categoryId;
        }
        if ($status !== null && in_array(strtoupper($status), self::STATUSES, true)) {
            $where[] = 'e.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($from !== null && $from !== '') {
            $where[] = 'e.expenseDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'e.expenseDate <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM expenses e JOIN expenseCategories c ON c.id = e.categoryId ' . $clause,
            $params
        );
        $totals = $this->db->first(
            'SELECT COALESCE(SUM(CASE WHEN e.status <> \'CANCELLED\' THEN e.amount ELSE 0 END), 0) AS amount,
                    COALESCE(SUM(CASE WHEN e.status = \'PAID\' THEN e.amount ELSE 0 END), 0) AS paid,
                    COALESCE(SUM(CASE WHEN e.status IN (\'DRAFT\', \'APPROVED\') THEN e.amount ELSE 0 END), 0) AS pending
             FROM expenses e JOIN expenseCategories c ON c.id = e.categoryId ' . $clause,
            $params
        ) ?? ['amount' => 0, 'paid' => 0, 'pending' => 0];

        $rows = $this->db->select(
            'SELECT e.*, c.code AS categoryCode, c.nameFr AS categoryName, c.nameAr AS categoryNameAr,
                    a.name AS cashAccountName, v.plate AS vehiclePlate, u.fullName AS creatorName,
                    s.name AS supplierName
             FROM expenses e
             JOIN expenseCategories c ON c.id = e.categoryId
             LEFT JOIN cashAccounts a ON a.id = e.cashAccountId
             LEFT JOIN vehicles v ON v.id = e.vehicleId
             LEFT JOIN suppliers s ON s.id = e.supplierId
             LEFT JOIN users u ON u.id = e.createdBy ' . $clause . '
             ORDER BY e.expenseDate DESC, e.id DESC
             LIMIT ' . $pageSize . ' OFFSET ' . max(0, ($page - 1) * $pageSize),
            $params
        );

        return [
            'rows' => array_map(fn (array $row): array => $this->hydrate($row), $rows),
            'total' => $total,
            'summary' => [
                'amount' => round((float) $totals['amount'], 2),
                'paid' => round((float) $totals['paid'], 2),
                'pending' => round((float) $totals['pending'], 2),
            ],
        ];
    }

    /** @return array<string,mixed> */
    public function expense(int $id): array
    {
        $row = $this->db->first(
            'SELECT e.*, c.code AS categoryCode, c.nameFr AS categoryName, c.nameAr AS categoryNameAr,
                    a.name AS cashAccountName, v.plate AS vehiclePlate, u.fullName AS creatorName,
                    s.name AS supplierName
             FROM expenses e
             JOIN expenseCategories c ON c.id = e.categoryId
             LEFT JOIN cashAccounts a ON a.id = e.cashAccountId
             LEFT JOIN vehicles v ON v.id = e.vehicleId
             LEFT JOIN suppliers s ON s.id = e.supplierId
             LEFT JOIN users u ON u.id = e.createdBy
             WHERE e.id = :id AND e.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Expense');
        }

        return $this->hydrate($row);
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function create(array $data, int $userId): array
    {
        $categoryId = (int) ($data['categoryId'] ?? 0);
        $category = $this->db->first(
            'SELECT id, code FROM expenseCategories WHERE id = :id AND isActive = 1',
            ['id' => $categoryId]
        );
        if ($category === null) {
            throw ApiException::validation(['categoryId' => 'validation.exists']);
        }
        $label = mb_substr(trim((string) ($data['label'] ?? '')), 0, 180);
        if ($label === '') {
            throw ApiException::validation(['label' => 'validation.required']);
        }
        $amount = round((float) ($data['amount'] ?? 0), 2);
        if ($amount <= 0) {
            throw ApiException::validation(['amount' => 'validation.positive']);
        }
        $vat = round((float) ($data['vatAmount'] ?? 0), 2);
        if ($vat < 0) {
            throw ApiException::validation(['vatAmount' => 'validation.positive']);
        }
        $method = strtoupper(trim((string) ($data['paymentMethod'] ?? 'CASH')));
        if (!in_array($method, self::METHODS, true)) {
            throw ApiException::validation(['paymentMethod' => 'validation.allowed_values']);
        }
        $date = $this->date($data['expenseDate'] ?? null) ?? date('Y-m-d');
        $cashAccountId = $this->optionalId($data['cashAccountId'] ?? null, 'cashAccounts', 'cashAccountId');
        $vehicleId = $this->optionalId($data['vehicleId'] ?? null, 'vehicles', 'vehicleId');
        $supplierId = $this->optionalId($data['supplierId'] ?? null, 'suppliers', 'supplierId');
        $employeeId = $this->optionalId($data['employeeId'] ?? null, 'employees', 'employeeId');

        $this->db->execute(
            'INSERT INTO expenses (expenseNumber, categoryId, label, amount, vatAmount, expenseDate,
                    paymentMethod, cashAccountId, supplierId, vehicleId, employeeId, attachmentPath, status,
                    notes, createdBy)
             VALUES (:number, :categoryId, :label, :amount, :vatAmount, :expenseDate, :paymentMethod,
                    :cashAccountId, :supplierId, :vehicleId, :employeeId, :attachmentPath, \'DRAFT\', :notes,
                    :createdBy)',
            [
                'number' => $this->nextNumber(),
                'categoryId' => $categoryId,
                'label' => $label,
                'amount' => $amount,
                'vatAmount' => $vat,
                'expenseDate' => $date,
                'paymentMethod' => $method,
                'cashAccountId' => $cashAccountId,
                'supplierId' => $supplierId,
                'vehicleId' => $vehicleId,
                'employeeId' => $employeeId,
                'attachmentPath' => $this->path($data['attachmentPath'] ?? null),
                'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                'createdBy' => $userId,
            ]
        );

        return $this->expense($this->db->lastId());
    }

    /** @return array<string,mixed> */
    public function approve(int $id, int $userId): array
    {
        $expense = $this->expense($id);
        if ($expense['status'] !== 'DRAFT') {
            throw ApiException::conflict('Only a draft expense can be approved.', 'INVALID_STATUS',
                ['status' => $expense['status']]);
        }
        $this->db->execute(
            'UPDATE expenses SET status = \'APPROVED\', approvedBy = :userId WHERE id = :id',
            ['userId' => $userId, 'id' => $id]
        );

        return $this->expense($id);
    }

    /**
     * Paying is the only moment money moves: the cash book row is written in the same transaction, so
     * a closed period refuses the payment and leaves the expense approved.
     *
     * @return array<string,mixed>
     */
    public function pay(int $id, int $cashAccountId, int $userId): array
    {
        $expense = $this->expense($id);
        if (!in_array($expense['status'], ['DRAFT', 'APPROVED'], true)) {
            throw ApiException::conflict('This expense cannot be paid in its current state.', 'INVALID_STATUS',
                ['status' => $expense['status']]);
        }
        if ($cashAccountId <= 0) {
            throw ApiException::validation(['cashAccountId' => 'validation.required']);
        }

        $this->db->transaction(function () use ($expense, $id, $cashAccountId, $userId): void {
            $this->db->execute(
                'UPDATE expenses SET status = \'PAID\', approvedBy = COALESCE(approvedBy, :userId),
                        cashAccountId = COALESCE(cashAccountId, :cashAccountId)
                 WHERE id = :id',
                ['userId' => $userId, 'cashAccountId' => $cashAccountId, 'id' => $id]
            );
            $this->finance->record([
                'cashAccountId' => $expense['cashAccountId'] ?? $cashAccountId,
                'direction' => 'OUT',
                'amount' => (float) $expense['amount'] + (float) $expense['vatAmount'],
                'sourceType' => 'EXPENSE',
                'sourceId' => $id,
                'description' => $expense['expenseNumber'] . ' - ' . $expense['label'],
                'occurredAt' => $expense['expenseDate'] . ' 12:00:00',
                'userId' => $userId,
            ]);
        });

        return $this->expense($id);
    }

    /** @return array<string,mixed> */
    public function cancel(int $id): array
    {
        $expense = $this->expense($id);
        if (!in_array($expense['status'], ['DRAFT', 'APPROVED'], true)) {
            throw ApiException::conflict('A paid expense is not cancelled: it is corrected by an opposite entry.',
                'INVALID_STATUS', ['status' => $expense['status']]);
        }
        $this->db->execute(
            'UPDATE expenses SET status = \'CANCELLED\', updatedAt = NOW() WHERE id = :id',
            ['id' => $id]
        );

        return $this->expense($id);
    }

    /** @return array<int,array> */
    public function summary(?string $from, ?string $to): array
    {
        $where = ['e.deletedAt IS NULL', 'e.status <> \'CANCELLED\''];
        $params = [];
        if ($from !== null && $from !== '') {
            $where[] = 'e.expenseDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'e.expenseDate <= :to';
            $params['to'] = $to;
        }

        $rows = $this->db->select(
            'SELECT c.code, c.nameFr, c.nameAr, COALESCE(SUM(e.amount), 0) AS amount, COUNT(*) AS entryCount
             FROM expenses e JOIN expenseCategories c ON c.id = e.categoryId
             WHERE ' . implode(' AND ', $where) . '
             GROUP BY c.id, c.code, c.nameFr, c.nameAr
             ORDER BY amount DESC',
            $params
        );

        return array_map(static fn (array $row): array => [
            'code' => (string) $row['code'],
            'nameFr' => (string) $row['nameFr'],
            'nameAr' => (string) $row['nameAr'],
            'amount' => round((float) $row['amount'], 2),
            'entryCount' => (int) $row['entryCount'],
        ], $rows);
    }

    // ------------------------------------------------------------------ mapping

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrate(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'expenseNumber' => (string) $row['expenseNumber'],
            'categoryId' => (int) $row['categoryId'],
            'categoryCode' => (string) ($row['categoryCode'] ?? ''),
            'categoryName' => (string) ($row['categoryName'] ?? ''),
            'categoryNameAr' => (string) ($row['categoryNameAr'] ?? ''),
            'label' => (string) $row['label'],
            'amount' => round((float) $row['amount'], 2),
            'vatAmount' => round((float) $row['vatAmount'], 2),
            'totalAmount' => round((float) $row['amount'] + (float) $row['vatAmount'], 2),
            'expenseDate' => (string) $row['expenseDate'],
            'paymentMethod' => (string) $row['paymentMethod'],
            'cashAccountId' => $row['cashAccountId'] === null ? null : (int) $row['cashAccountId'],
            'cashAccountName' => $row['cashAccountName'] ?? null,
            'supplierId' => $row['supplierId'] === null ? null : (int) $row['supplierId'],
            'supplierName' => $row['supplierName'] ?? null,
            'vehicleId' => $row['vehicleId'] === null ? null : (int) $row['vehicleId'],
            'vehiclePlate' => $row['vehiclePlate'] ?? null,
            'employeeId' => $row['employeeId'] === null ? null : (int) $row['employeeId'],
            'attachmentPath' => $row['attachmentPath'] ?? null,
            'status' => (string) $row['status'],
            'notes' => (string) $row['notes'],
            'createdBy' => $row['createdBy'] === null ? null : (int) $row['createdBy'],
            'creatorName' => $row['creatorName'] ?? null,
            'approvedBy' => $row['approvedBy'] === null ? null : (int) $row['approvedBy'],
        ];
    }

    private function optionalId(mixed $value, string $table, string $field): ?int
    {
        $id = (int) ($value ?? 0);
        if ($id <= 0) {
            return null;
        }
        $row = $this->db->first('SELECT id FROM ' . $table . ' WHERE id = :id', ['id' => $id]);
        if ($row === null) {
            throw ApiException::validation([$field => 'validation.exists']);
        }

        return $id;
    }

    private function path(mixed $value): ?string
    {
        $path = trim((string) ($value ?? ''));

        return $path === '' ? null : mb_substr($path, 0, 255);
    }

    private function date(mixed $value): ?string
    {
        $raw = trim((string) ($value ?? ''));
        if ($raw === '') {
            return null;
        }
        $parsed = \DateTimeImmutable::createFromFormat('Y-m-d', $raw);

        return $parsed === false ? null : $parsed->format('Y-m-d');
    }

    private function nextNumber(): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM expenses') + 1;
        do {
            $number = sprintf('DP-%05d', $sequence);
            $exists = $this->db->first('SELECT id FROM expenses WHERE expenseNumber = :number',
                ['number' => $number]) !== null;
            $sequence++;
        } while ($exists);

        return $number;
    }
}
