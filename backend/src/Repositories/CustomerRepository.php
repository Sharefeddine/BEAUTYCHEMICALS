<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Customer master data, the receivable each customer carries, and its statement.
 *
 * <p>A customer is never deleted once money moved: the row is deactivated instead, so an invoice
 * keeps naming a customer that exists. The balance is the sum of what the customer owes
 * (issued invoices) minus what was settled (payments and applied credit notes); it is written by
 * {@see SalesRepository} and this class only reads it, which keeps one writer per figure.</p>
 */
final class CustomerRepository
{
    private const SEGMENTS = ['RETAIL', 'WHOLESALE', 'SPECIAL'];
    private const TYPES = ['INDIVIDUAL', 'COMPANY'];

    public function __construct(private readonly Database $db)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, ?string $segment, bool $activeOnly): array
    {
        $where = ['c.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(c.code LIKE :searchCode OR c.name LIKE :searchName OR c.phone LIKE :searchPhone
                         OR c.wilaya LIKE :searchWilaya)';
            $term = '%' . $search . '%';
            $params['searchCode'] = $term;
            $params['searchName'] = $term;
            $params['searchPhone'] = $term;
            $params['searchWilaya'] = $term;
        }
        if ($segment !== null && $segment !== '' && in_array(strtoupper($segment), self::SEGMENTS, true)) {
            $where[] = 'c.priceSegment = :segment';
            $params['segment'] = strtoupper($segment);
        }
        if ($activeOnly) {
            $where[] = 'c.isActive = 1';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM customers c ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT c.*, (SELECT COUNT(*) FROM invoices i WHERE i.customerId = c.id AND i.status <> \'CANCELLED\')
                        AS invoiceCount,
                    (SELECT COALESCE(SUM(i.totalTtc - i.paidAmount), 0) FROM invoices i
                        WHERE i.customerId = c.id AND i.status IN (\'ISSUED\', \'PARTIAL\')) AS outstanding,
                    (SELECT COUNT(*) FROM salesOrders o WHERE o.customerId = c.id) AS orderCount
             FROM customers c ' . $clause . '
             ORDER BY c.name
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrate'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function find(int $id): array
    {
        $row = $this->db->first(
            'SELECT c.*, (SELECT COUNT(*) FROM invoices i WHERE i.customerId = c.id AND i.status <> \'CANCELLED\')
                        AS invoiceCount,
                    (SELECT COALESCE(SUM(i.totalTtc - i.paidAmount), 0) FROM invoices i
                        WHERE i.customerId = c.id AND i.status IN (\'ISSUED\', \'PARTIAL\')) AS outstanding,
                    (SELECT COUNT(*) FROM salesOrders o WHERE o.customerId = c.id) AS orderCount
             FROM customers c WHERE c.id = :id AND c.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Customer');
        }

        $customer = $this->hydrate($row);
        $customer['orders'] = $this->db->select(
            'SELECT o.id, o.orderNumber, o.status, o.orderDate, o.totalTtc, o.deliveryDate,
                    (SELECT COUNT(*) FROM deliveryNotes d WHERE d.orderId = o.id) AS deliveryCount
             FROM salesOrders o WHERE o.customerId = :id
             ORDER BY o.orderDate DESC, o.id DESC LIMIT 10',
            ['id' => $id]
        );
        $customer['invoices'] = $this->db->select(
            'SELECT i.id, i.invoiceNumber, i.status, i.invoiceDate, i.dueDate, i.totalTtc, i.paidAmount
             FROM invoices i WHERE i.customerId = :id ORDER BY i.invoiceDate DESC, i.id DESC LIMIT 10',
            ['id' => $id]
        );
        $customer['topProducts'] = $this->db->select(
            'SELECT p.sku AS productCode, p.nameFr AS productName, SUM(ii.quantity) AS quantity,
                    SUM(ii.lineTotal) AS revenue
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId
             JOIN products p ON p.id = ii.productId
             WHERE i.customerId = :id AND i.status <> \'CANCELLED\'
             GROUP BY p.id, p.sku, p.nameFr ORDER BY revenue DESC LIMIT 5',
            ['id' => $id]
        );

        return $customer;
    }

    /**
     * Statement of account: every invoice, payment and applied credit note, oldest first, with the
     * running balance the customer's accountant expects.
     *
     * <p>The three documents live in three tables, so they are read separately and merged here:
     * a UNION would need every filter bound once per branch, which server-side prepared statements
     * refuse.</p>
     *
     * @return array<string,mixed>
     */
    public function statement(int $id, ?string $from, ?string $to): array
    {
        $customer = $this->find($id);

        $range = '';
        $params = ['id' => $id];
        if ($from !== null && $from !== '') {
            $range .= ' AND invoiceDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $range .= ' AND invoiceDate <= :to';
            $params['to'] = $to;
        }

        $rows = [];
        foreach ($this->db->select(
            'SELECT id, invoiceNumber AS documentNumber, DATE(invoiceDate) AS documentDate,
                    totalTtc AS debit, 0 AS credit, status AS documentStatus
             FROM invoices WHERE customerId = :id AND status <> \'CANCELLED\'' . $range,
            $params
        ) as $row) {
            $rows[] = $row + ['kind' => 'INVOICE'];
        }

        $paymentRange = '';
        if ($from !== null && $from !== '') {
            $paymentRange .= ' AND DATE(p.paidAt) >= :from';
        }
        if ($to !== null && $to !== '') {
            $paymentRange .= ' AND DATE(p.paidAt) <= :to';
        }
        foreach ($this->db->select(
            'SELECT p.id, p.paymentNumber AS documentNumber, DATE(p.paidAt) AS documentDate,
                    0 AS debit, p.amount AS credit, p.method AS documentStatus
             FROM payments p
             WHERE p.customerId = :id' . $paymentRange,
            $params
        ) as $row) {
            $rows[] = $row + ['kind' => 'PAYMENT'];
        }

        $creditRange = '';
        if ($from !== null && $from !== '') {
            $creditRange .= ' AND c.creditDate >= :from';
        }
        if ($to !== null && $to !== '') {
            $creditRange .= ' AND c.creditDate <= :to';
        }
        foreach ($this->db->select(
            'SELECT c.id, c.creditNumber AS documentNumber, DATE(c.creditDate) AS documentDate,
                    0 AS debit, c.totalTtc AS credit, c.status AS documentStatus
             FROM creditNotes c
             WHERE c.customerId = :id AND c.status <> \'CANCELLED\'' . $creditRange,
            $params
        ) as $row) {
            $rows[] = $row + ['kind' => 'CREDIT'];
        }

        usort($rows, static function (array $left, array $right): int {
            return [$left['documentDate'], $left['kind'], (int) $left['id']]
                <=> [$right['documentDate'], $right['kind'], (int) $right['id']];
        });

        $running = 0.0;
        $lines = [];
        foreach ($rows as $row) {
            $debit = round((float) $row['debit'], 2);
            $credit = round((float) $row['credit'], 2);
            $running += $debit - $credit;
            $lines[] = [
                'kind' => (string) $row['kind'],
                'documentId' => (int) $row['id'],
                'documentNumber' => (string) $row['documentNumber'],
                'documentDate' => (string) $row['documentDate'],
                'status' => (string) $row['documentStatus'],
                'debit' => $debit,
                'credit' => $credit,
                'balance' => round($running, 2),
            ];
        }

        return [
            'customer' => [
                'id' => $customer['id'],
                'code' => $customer['code'],
                'name' => $customer['name'],
                'wilaya' => $customer['wilaya'],
                'phone' => $customer['phone'],
                'creditLimit' => $customer['creditLimit'],
                'balance' => $customer['balance'],
            ],
            'from' => $from,
            'to' => $to,
            'lines' => $lines,
            'totalDebit' => round(array_sum(array_column($lines, 'debit')), 2),
            'totalCredit' => round(array_sum(array_column($lines, 'credit')), 2),
            'closingBalance' => round($running, 2),
        ];
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function create(array $data, int $userId): array
    {
        $name = trim((string) ($data['name'] ?? ''));
        if ($name === '') {
            throw ApiException::validation(['name' => 'validation.required']);
        }
        $this->assertEditable($data);

        $id = $this->db->transaction(function () use ($data, $name, $userId): int {
            $this->db->execute(
                'INSERT INTO customers (code, name, type, phone, email, address, wilaya, creditLimit, balance,
                        priceSegment, notes, isActive, createdBy, updatedBy)
                 VALUES (:code, :name, :type, :phone, :email, :address, :wilaya, :creditLimit, 0,
                        :priceSegment, :notes, 1, :createdBy, :updatedBy)',
                [
                    'code' => $this->nextCode(),
                    'name' => $name,
                    'type' => $this->type($data),
                    'phone' => mb_substr(trim((string) ($data['phone'] ?? '')), 0, 40),
                    'email' => mb_substr(trim((string) ($data['email'] ?? '')), 0, 120),
                    'address' => mb_substr(trim((string) ($data['address'] ?? '')), 0, 255),
                    'wilaya' => mb_substr(trim((string) ($data['wilaya'] ?? '')), 0, 80),
                    'creditLimit' => round((float) ($data['creditLimit'] ?? 0), 2),
                    'priceSegment' => $this->segment($data),
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                    'createdBy' => $userId,
                    'updatedBy' => $userId,
                ]
            );

            return $this->db->lastId();
        });

        return $this->find($id);
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function update(int $id, array $data, int $userId): array
    {
        $current = $this->find($id);
        if (array_key_exists('name', $data) && trim((string) $data['name']) === '') {
            throw ApiException::validation(['name' => 'validation.required']);
        }
        $this->assertEditable($data);

        $creditLimit = array_key_exists('creditLimit', $data)
            ? round((float) $data['creditLimit'], 2) : $current['creditLimit'];
        if ($creditLimit > 0 && $creditLimit < (float) $current['balance']) {
            throw ApiException::conflict(
                'The credit limit cannot be lowered below the amount the customer already owes.',
                'CREDIT_LIMIT_BELOW_BALANCE',
                ['balance' => $current['balance'], 'creditLimit' => $creditLimit]
            );
        }

        $this->db->execute(
            'UPDATE customers SET name = :name, type = :type, phone = :phone, email = :email, address = :address,
                    wilaya = :wilaya, creditLimit = :creditLimit, priceSegment = :priceSegment, notes = :notes,
                    isActive = :isActive, updatedBy = :updatedBy
             WHERE id = :id',
            [
                'name' => array_key_exists('name', $data) ? trim((string) $data['name']) : $current['name'],
                'type' => array_key_exists('type', $data) ? $this->type($data) : $current['type'],
                'phone' => array_key_exists('phone', $data) ? mb_substr(trim((string) $data['phone']), 0, 40)
                    : $current['phone'],
                'email' => array_key_exists('email', $data) ? mb_substr(trim((string) $data['email']), 0, 120)
                    : $current['email'],
                'address' => array_key_exists('address', $data)
                    ? mb_substr(trim((string) $data['address']), 0, 255) : $current['address'],
                'wilaya' => array_key_exists('wilaya', $data)
                    ? mb_substr(trim((string) $data['wilaya']), 0, 80) : $current['wilaya'],
                'creditLimit' => $creditLimit,
                'priceSegment' => array_key_exists('priceSegment', $data) ? $this->segment($data)
                    : $current['priceSegment'],
                'notes' => array_key_exists('notes', $data)
                    ? mb_substr(trim((string) $data['notes']), 0, 255) : (string) $current['notes'],
                'isActive' => array_key_exists('isActive', $data)
                    ? ((bool) $data['isActive'] ? 1 : 0) : ((bool) $current['isActive'] ? 1 : 0),
                'updatedBy' => $userId,
                'id' => $id,
            ]
        );

        return $this->find($id);
    }

    /**
     * A customer with an open balance is deactivated, never deleted: the invoices must keep naming
     * someone.
     *
     * @return array<string,mixed>
     */
    public function deactivate(int $id, int $userId): array
    {
        $customer = $this->find($id);
        if (abs((float) $customer['balance']) > 0.009) {
            throw ApiException::conflict(
                'The customer still owes money: settle the balance before closing the account.',
                'CUSTOMER_HAS_BALANCE',
                ['balance' => $customer['balance']]
            );
        }

        $this->db->execute('UPDATE customers SET isActive = 0, deletedAt = NOW(), updatedBy = :userId WHERE id = :id',
            ['userId' => $userId, 'id' => $id]);

        return $this->findIncludingClosed($id);
    }

    /**
     * Customers that may buy today: active and not deleted.
     *
     * @return array<int,array>
     */
    public function selectable(): array
    {
        return $this->db->select(
            'SELECT id, code, name, priceSegment, creditLimit, balance
             FROM customers WHERE isActive = 1 AND deletedAt IS NULL ORDER BY name'
        );
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrate(array $row): array
    {
        $creditLimit = round((float) $row['creditLimit'], 2);
        $balance = round((float) $row['balance'], 2);
        $available = $creditLimit > 0 ? round($creditLimit - $balance, 2) : null;

        return [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'name' => (string) $row['name'],
            'type' => (string) $row['type'],
            'phone' => (string) $row['phone'],
            'email' => (string) $row['email'],
            'address' => (string) $row['address'],
            'wilaya' => (string) $row['wilaya'],
            'creditLimit' => $creditLimit,
            'balance' => $balance,
            'availableCredit' => $available,
            'priceSegment' => (string) $row['priceSegment'],
            'notes' => (string) ($row['notes'] ?? ''),
            'isActive' => (bool) $row['isActive'],
            'invoiceCount' => (int) ($row['invoiceCount'] ?? 0),
            'orderCount' => (int) ($row['orderCount'] ?? 0),
            'outstanding' => round((float) ($row['outstanding'] ?? 0), 2),
            'createdAt' => $row['createdAt'],
        ];
    }

    /** @return array<string,mixed> */
    private function findIncludingClosed(int $id): array
    {
        $row = $this->db->first('SELECT * FROM customers WHERE id = :id', ['id' => $id]);
        if ($row === null) {
            throw ApiException::notFound('Customer');
        }

        return $this->hydrate($row);
    }

    /** @param array<string,mixed> $data */
    private function assertEditable(array $data): void
    {
        if (array_key_exists('creditLimit', $data) && (float) $data['creditLimit'] < 0) {
            throw ApiException::validation(['creditLimit' => 'validation.range']);
        }
        if (array_key_exists('email', $data) && (string) $data['email'] !== ''
            && filter_var((string) $data['email'], FILTER_VALIDATE_EMAIL) === false) {
            throw ApiException::validation(['email' => 'validation.email']);
        }
        if (array_key_exists('type', $data)
            && !in_array(strtoupper((string) $data['type']), self::TYPES, true)) {
            throw ApiException::validation(['type' => 'validation.allowed_values']);
        }
        if (array_key_exists('priceSegment', $data)
            && !in_array(strtoupper((string) $data['priceSegment']), self::SEGMENTS, true)) {
            throw ApiException::validation(['priceSegment' => 'validation.allowed_values']);
        }
    }

    /** @param array<string,mixed> $data */
    private function type(array $data): string
    {
        $type = strtoupper(trim((string) ($data['type'] ?? 'COMPANY')));

        return in_array($type, self::TYPES, true) ? $type : 'COMPANY';
    }

    /** @param array<string,mixed> $data */
    private function segment(array $data): string
    {
        $segment = strtoupper(trim((string) ($data['priceSegment'] ?? 'RETAIL')));

        return in_array($segment, self::SEGMENTS, true) ? $segment : 'RETAIL';
    }

    private function nextCode(): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM customers') + 1;
        do {
            $code = sprintf('CL-%04d', $sequence);
            $exists = $this->db->first('SELECT id FROM customers WHERE code = :code', ['code' => $code]) !== null;
            $sequence++;
        } while ($exists);

        return $code;
    }
}
