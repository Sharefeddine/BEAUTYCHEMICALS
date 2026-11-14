<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * The sales cycle: quotation -> order -> delivery note -> invoice -> payment, plus credit notes.
 *
 * <p>The chain is deliberately one-way. Each step only accepts the status the previous step leaves
 * behind, so a figure can never be invented twice:</p>
 * <ul>
 *   <li>a quotation is priced from the customer's segment and copied into an order, never edited after
 *       it was accepted;</li>
 *   <li>confirming an order walks through the credit limit: a customer who already owes more than the
 *       order is worth cannot confirm it;</li>
 *   <li>a delivery note consumes released batches FEFO and is the only place stock leaves the
 *       warehouse; {@code deliveredQty} follows it;</li>
 *   <li>an invoice can only bill what was delivered, and once issued it is frozen — corrections go
 *       through a credit note;</li>
 *   <li>a payment settles an invoice (never more than what remains) or sits on account.</li>
 * </ul>
 */
final class SalesRepository
{
    private const QUOTE_STATUSES = ['DRAFT', 'SENT', 'ACCEPTED', 'EXPIRED', 'REJECTED'];
    private const ORDER_STATUSES = ['DRAFT', 'CONFIRMED', 'RESERVED', 'PARTIAL', 'INVOICED', 'CANCELLED'];
    private const DELIVERY_STATUSES = ['PREPARED', 'LOADED', 'IN_TRANSIT', 'DELIVERED', 'RETURNED'];
    private const INVOICE_STATUSES = ['DRAFT', 'ISSUED', 'PARTIAL', 'PAID', 'CANCELLED'];
    private const PAYMENT_METHODS = ['CASH', 'CHEQUE', 'TRANSFER', 'CARD', 'CREDIT'];

    public function __construct(
        private readonly Database $db,
        private readonly InventoryRepository $inventory,
        private readonly CustomerRepository $customers
    ) {
    }

    // ---------------------------------------------------------------- quotations

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateQuotations(int $page, int $pageSize, string $search, ?string $status,
                                       ?int $customerId, ?string $from, ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(q.quoteNumber LIKE :searchNumber OR c.name LIKE :searchCustomer OR c.code LIKE :searchCode)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchCustomer'] = $term;
            $params['searchCode'] = $term;
        }
        if ($status !== null && in_array(strtoupper($status), self::QUOTE_STATUSES, true)) {
            $where[] = 'q.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($customerId !== null && $customerId > 0) {
            $where[] = 'q.customerId = :customerId';
            $params['customerId'] = $customerId;
        }
        if ($from !== null && $from !== '') {
            $where[] = 'q.quoteDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'q.quoteDate <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM quotations q JOIN customers c ON c.id = q.customerId ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT q.*, c.name AS customerName, c.code AS customerCode, c.priceSegment,
                    (SELECT COUNT(*) FROM quotationItems qi WHERE qi.quotationId = q.id) AS lineCount,
                    (SELECT COUNT(*) FROM salesOrders o WHERE o.quotationId = q.id) AS orderCount
             FROM quotations q JOIN customers c ON c.id = q.customerId ' . $clause . '
             ORDER BY q.quoteDate DESC, q.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateQuotation'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function quotation(int $id): array
    {
        $row = $this->db->first(
            'SELECT q.*, c.name AS customerName, c.code AS customerCode, c.priceSegment, c.phone AS customerPhone,
                    c.address AS customerAddress, c.wilaya AS customerWilaya,
                    u.fullName AS representativeName
             FROM quotations q
             JOIN customers c ON c.id = q.customerId
             LEFT JOIN users u ON u.id = q.representativeId
             WHERE q.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Quotation');
        }

        $quotation = $this->hydrateQuotation($row);
        $quotation['items'] = $this->db->select(
            'SELECT qi.id, qi.productId, p.sku AS productCode, p.nameFr AS productName, p.unit,
                    qi.quantity, qi.unitPrice, qi.discountPct, qi.vatRate,
                    ROUND(qi.quantity * qi.unitPrice * (1 - qi.discountPct / 100), 2) AS lineTotal
             FROM quotationItems qi JOIN products p ON p.id = qi.productId
             WHERE qi.quotationId = :id ORDER BY qi.id',
            ['id' => $id]
        );
        $quotation['orders'] = $this->db->select(
            'SELECT id, orderNumber, status, orderDate, totalTtc FROM salesOrders
             WHERE quotationId = :id ORDER BY id',
            ['id' => $id]
        );

        return $quotation;
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function createQuotation(array $data, int $userId): array
    {
        $customer = $this->customer((int) ($data['customerId'] ?? 0));
        $lines = $this->lines($data, (string) $customer['priceSegment']);
        [$amountHt, $vatAmount] = $this->totals($lines);

        $id = $this->db->transaction(function () use ($data, $customer, $lines, $amountHt, $vatAmount, $userId): int {
            $this->db->execute(
                'INSERT INTO quotations (quoteNumber, customerId, status, quoteDate, validUntil, amountHt, vatAmount,
                        totalTtc, representativeId, notes, createdBy)
                 VALUES (:number, :customerId, \'DRAFT\', :quoteDate, :validUntil, :amountHt, :vatAmount,
                        :totalTtc, :representativeId, :notes, :createdBy)',
                [
                    'number' => $this->nextNumber('quotations', 'quoteNumber', 'DV'),
                    'customerId' => $customer['id'],
                    'quoteDate' => $this->date($data['quoteDate'] ?? null) ?? date('Y-m-d'),
                    'validUntil' => $this->date($data['validUntil'] ?? null),
                    'amountHt' => $amountHt,
                    'vatAmount' => $vatAmount,
                    'totalTtc' => round($amountHt + $vatAmount, 2),
                    'representativeId' => $userId,
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                    'createdBy' => $userId,
                ]
            );
            $quotationId = $this->db->lastId();
            $this->insertQuotationItems($quotationId, $lines);

            return $quotationId;
        });

        return $this->quotation($id);
    }

    /** @return array<string,mixed> */
    public function sendQuotation(int $id, int $userId): array
    {
        $quotation = $this->quotation($id);
        if ($quotation['status'] !== 'DRAFT') {
            throw $this->wrongStatus('Quotation', $quotation['status'], ['DRAFT']);
        }
        $this->db->execute('UPDATE quotations SET status = \'SENT\' WHERE id = :id', ['id' => $id]);

        return $this->quotation($id);
    }

    /**
     * Accepting a quotation converts it into an order in the same transaction: a quote that is
     * accepted but has no order would sit in the pipeline forever.
     *
     * @return array{quotation:array<string,mixed>,order:array<string,mixed>}
     */
    public function acceptQuotation(int $id, int $userId, ?string $deliveryDate = null): array
    {
        $quotation = $this->quotation($id);
        if (!in_array($quotation['status'], ['DRAFT', 'SENT'], true)) {
            throw $this->wrongStatus('Quotation', $quotation['status'], ['DRAFT', 'SENT']);
        }

        $orderNumber = $this->db->transaction(function () use ($id, $quotation, $userId, $deliveryDate): string {
            $this->db->execute('UPDATE quotations SET status = \'ACCEPTED\' WHERE id = :id', ['id' => $id]);

            $number = $this->nextNumber('salesOrders', 'orderNumber', 'SO');
            $this->db->execute(
                'INSERT INTO salesOrders (orderNumber, customerId, quotationId, status, orderDate, deliveryDate,
                        amountHt, vatAmount, totalTtc, representativeId, notes, createdBy, updatedBy)
                 VALUES (:number, :customerId, :quotationId, \'DRAFT\', :orderDate, :deliveryDate, :amountHt,
                        :vatAmount, :totalTtc, :representativeId, :notes, :createdBy, :updatedBy)',
                [
                    'number' => $number,
                    'customerId' => $quotation['customerId'],
                    'quotationId' => $id,
                    'orderDate' => date('Y-m-d'),
                    'deliveryDate' => $deliveryDate,
                    'amountHt' => $quotation['amountHt'],
                    'vatAmount' => $quotation['vatAmount'],
                    'totalTtc' => $quotation['totalTtc'],
                    'representativeId' => $userId,
                    'notes' => 'Depuis le devis ' . $quotation['quoteNumber'],
                    'createdBy' => $userId,
                    'updatedBy' => $userId,
                ]
            );
            $orderId = $this->db->lastId();

            foreach ($quotation['items'] as $item) {
                $this->db->execute(
                    'INSERT INTO salesOrderItems (orderId, productId, quantity, unitPrice, discountPct, vatRate)
                     VALUES (:orderId, :productId, :quantity, :unitPrice, :discountPct, :vatRate)',
                    [
                        'orderId' => $orderId,
                        'productId' => (int) $item['productId'],
                        'quantity' => (float) $item['quantity'],
                        'unitPrice' => (float) $item['unitPrice'],
                        'discountPct' => (float) $item['discountPct'],
                        'vatRate' => (float) $item['vatRate'],
                    ]
                );
            }

            return $number;
        });

        $order = $this->db->first('SELECT id FROM salesOrders WHERE orderNumber = :number', ['number' => $orderNumber]);

        return [
            'quotation' => $this->quotation($id),
            'order' => $this->order((int) $order['id']),
        ];
    }

    /** @return array<string,mixed> */
    public function rejectQuotation(int $id): array
    {
        $quotation = $this->quotation($id);
        if (!in_array($quotation['status'], ['DRAFT', 'SENT'], true)) {
            throw $this->wrongStatus('Quotation', $quotation['status'], ['DRAFT', 'SENT']);
        }
        $this->db->execute('UPDATE quotations SET status = \'REJECTED\' WHERE id = :id', ['id' => $id]);

        return $this->quotation($id);
    }

    // -------------------------------------------------------------------- orders

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateOrders(int $page, int $pageSize, string $search, ?string $status,
                                   ?int $customerId, ?string $from, ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(o.orderNumber LIKE :searchNumber OR c.name LIKE :searchCustomer OR c.code LIKE :searchCode)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchCustomer'] = $term;
            $params['searchCode'] = $term;
        }
        if ($status !== null && in_array(strtoupper($status), self::ORDER_STATUSES, true)) {
            $where[] = 'o.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($customerId !== null && $customerId > 0) {
            $where[] = 'o.customerId = :customerId';
            $params['customerId'] = $customerId;
        }
        if ($from !== null && $from !== '') {
            $where[] = 'o.orderDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'o.orderDate <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM salesOrders o JOIN customers c ON c.id = o.customerId ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT o.*, c.name AS customerName, c.code AS customerCode, c.priceSegment, c.balance,
                    (SELECT COUNT(*) FROM salesOrderItems oi WHERE oi.orderId = o.id) AS lineCount,
                    (SELECT COALESCE(SUM(oi.quantity), 0) FROM salesOrderItems oi WHERE oi.orderId = o.id)
                        AS orderedQty,
                    (SELECT COALESCE(SUM(oi.deliveredQty), 0) FROM salesOrderItems oi WHERE oi.orderId = o.id)
                        AS deliveredQty,
                    (SELECT COALESCE(SUM(oi.invoicedQty), 0) FROM salesOrderItems oi WHERE oi.orderId = o.id)
                        AS invoicedQty,
                    (SELECT COUNT(*) FROM deliveryNotes d WHERE d.orderId = o.id) AS deliveryCount
             FROM salesOrders o JOIN customers c ON c.id = o.customerId ' . $clause . '
             ORDER BY o.orderDate DESC, o.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateOrder'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function order(int $id): array
    {
        $row = $this->db->first(
            'SELECT o.*, c.name AS customerName, c.code AS customerCode, c.priceSegment, c.balance,
                    c.creditLimit, c.phone AS customerPhone, c.address AS customerAddress,
                    c.wilaya AS customerWilaya, u.fullName AS representativeName, q.quoteNumber
             FROM salesOrders o
             JOIN customers c ON c.id = o.customerId
             LEFT JOIN users u ON u.id = o.representativeId
             LEFT JOIN quotations q ON q.id = o.quotationId
             WHERE o.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Sales order');
        }

        $order = $this->hydrateOrder($row);
        $order['items'] = $this->db->select(
            'SELECT oi.id, oi.productId, p.sku AS productCode, p.nameFr AS productName, p.unit,
                    oi.quantity, oi.deliveredQty, oi.invoicedQty, oi.unitPrice, oi.discountPct, oi.vatRate,
                    ROUND(oi.quantity * oi.unitPrice * (1 - oi.discountPct / 100), 2) AS lineTotal,
                    ROUND(oi.deliveredQty * oi.unitPrice * (1 - oi.discountPct / 100), 2) AS deliveredTotal
             FROM salesOrderItems oi JOIN products p ON p.id = oi.productId
             WHERE oi.orderId = :id ORDER BY oi.id',
            ['id' => $id]
        );
        $order['deliveries'] = $this->db->select(
            'SELECT id, noteNumber, status, preparedAt, deliveredAt, signedBy,
                    (SELECT COUNT(*) FROM deliveryItems di WHERE di.deliveryNoteId = deliveryNotes.id) AS lineCount
             FROM deliveryNotes WHERE orderId = :id ORDER BY id',
            ['id' => $id]
        );
        $order['invoices'] = $this->db->select(
            'SELECT id, invoiceNumber, status, invoiceDate, dueDate, totalTtc, paidAmount
             FROM invoices WHERE orderId = :id ORDER BY id',
            ['id' => $id]
        );

        return $order;
    }

    /** @param array<string,mixed> $data @return array<string,mixed> */
    public function createOrder(array $data, int $userId): array
    {
        $quotationId = (int) ($data['quotationId'] ?? 0);
        if ($quotationId > 0) {
            $quotation = $this->quotation($quotationId);
            if (!in_array($quotation['status'], ['DRAFT', 'SENT', 'ACCEPTED'], true)) {
                throw $this->wrongStatus('Quotation', $quotation['status'], ['DRAFT', 'SENT', 'ACCEPTED']);
            }
            $customerId = (int) $quotation['customerId'];
        } else {
            $customerId = (int) ($data['customerId'] ?? 0);
        }

        $customer = $this->customer($customerId);
        $lines = $quotationId > 0 && !isset($data['items'])
            ? array_map(static fn (array $item): array => [
                'productId' => (int) $item['productId'],
                'quantity' => (float) $item['quantity'],
                'unitPrice' => (float) $item['unitPrice'],
                'discountPct' => (float) $item['discountPct'],
                'vatRate' => (float) $item['vatRate'],
            ], $this->quotation($quotationId)['items'])
            : $this->lines($data, (string) $customer['priceSegment']);

        [$amountHt, $vatAmount] = $this->totals($lines);

        $id = $this->db->transaction(function () use ($data, $customer, $quotationId, $lines, $amountHt, $vatAmount,
            $userId): int {
            $this->db->execute(
                'INSERT INTO salesOrders (orderNumber, customerId, quotationId, status, orderDate, deliveryDate,
                        amountHt, vatAmount, totalTtc, representativeId, notes, createdBy, updatedBy)
                 VALUES (:number, :customerId, :quotationId, \'DRAFT\', :orderDate, :deliveryDate, :amountHt,
                        :vatAmount, :totalTtc, :representativeId, :notes, :createdBy, :updatedBy)',
                [
                    'number' => $this->nextNumber('salesOrders', 'orderNumber', 'SO'),
                    'customerId' => $customer['id'],
                    'quotationId' => $quotationId > 0 ? $quotationId : null,
                    'orderDate' => $this->date($data['orderDate'] ?? null) ?? date('Y-m-d'),
                    'deliveryDate' => $this->date($data['deliveryDate'] ?? null),
                    'amountHt' => $amountHt,
                    'vatAmount' => $vatAmount,
                    'totalTtc' => round($amountHt + $vatAmount, 2),
                    'representativeId' => $userId,
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                    'createdBy' => $userId,
                    'updatedBy' => $userId,
                ]
            );
            $orderId = $this->db->lastId();
            foreach ($lines as $line) {
                $this->db->execute(
                    'INSERT INTO salesOrderItems (orderId, productId, quantity, unitPrice, discountPct, vatRate)
                     VALUES (:orderId, :productId, :quantity, :unitPrice, :discountPct, :vatRate)',
                    [
                        'orderId' => $orderId,
                        'productId' => $line['productId'],
                        'quantity' => $line['quantity'],
                        'unitPrice' => $line['unitPrice'],
                        'discountPct' => $line['discountPct'],
                        'vatRate' => $line['vatRate'],
                    ]
                );
            }

            return $orderId;
        });

        return $this->order($id);
    }

    /**
     * Confirming an order is the credit decision: the customer's outstanding balance plus this order
     * may not exceed the limit (a limit of 0 means "no limit", which is how cash customers are set up).
     *
     * @return array<string,mixed>
     */
    public function confirmOrder(int $id, int $userId): array
    {
        $order = $this->order($id);
        if ($order['status'] !== 'DRAFT') {
            throw $this->wrongStatus('Sales order', $order['status'], ['DRAFT']);
        }
        $this->creditGuard((int) $order['customerId'], (float) $order['totalTtc'], 'order');

        $this->db->execute('UPDATE salesOrders SET status = \'CONFIRMED\', updatedBy = :userId WHERE id = :id',
            ['userId' => $userId, 'id' => $id]);

        return $this->order($id);
    }

    /** @return array<string,mixed> */
    public function cancelOrder(int $id, int $userId): array
    {
        $order = $this->order($id);
        if (in_array($order['status'], ['INVOICED', 'CANCELLED'], true)) {
            throw $this->wrongStatus('Sales order', $order['status'], ['DRAFT', 'CONFIRMED', 'RESERVED', 'PARTIAL']);
        }
        $hasDeliveries = $this->db->first(
            'SELECT id FROM deliveryNotes WHERE orderId = :id AND status <> \'RETURNED\' LIMIT 1', ['id' => $id]);
        if ($hasDeliveries !== null) {
            throw ApiException::conflict(
                'Goods already left the warehouse for this order: return the delivery note instead of cancelling.',
                'ORDER_HAS_DELIVERIES'
            );
        }
        $this->db->execute('UPDATE salesOrders SET status = \'CANCELLED\', updatedBy = :userId WHERE id = :id',
            ['userId' => $userId, 'id' => $id]);

        return $this->order($id);
    }

    // ---------------------------------------------------------------- deliveries

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateDeliveries(int $page, int $pageSize, string $search, ?string $status,
                                       ?int $customerId, ?string $from, ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(d.noteNumber LIKE :searchNumber OR c.name LIKE :searchCustomer OR o.orderNumber LIKE :searchOrder)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchCustomer'] = $term;
            $params['searchOrder'] = $term;
        }
        if ($status !== null && in_array(strtoupper($status), self::DELIVERY_STATUSES, true)) {
            $where[] = 'd.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($customerId !== null && $customerId > 0) {
            $where[] = 'd.customerId = :customerId';
            $params['customerId'] = $customerId;
        }
        if ($from !== null && $from !== '') {
            $where[] = 'DATE(d.preparedAt) >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'DATE(d.preparedAt) <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM deliveryNotes d JOIN customers c ON c.id = d.customerId
             JOIN salesOrders o ON o.id = d.orderId ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT d.*, c.name AS customerName, c.code AS customerCode, o.orderNumber, o.totalTtc AS orderTotal,
                    (SELECT COUNT(*) FROM deliveryItems di WHERE di.deliveryNoteId = d.id) AS lineCount,
                    (SELECT COALESCE(SUM(di.quantity), 0) FROM deliveryItems di WHERE di.deliveryNoteId = d.id)
                        AS totalQuantity,
                    r.routeNumber
             FROM deliveryNotes d
             JOIN customers c ON c.id = d.customerId
             JOIN salesOrders o ON o.id = d.orderId
             LEFT JOIN deliveryRoutes r ON r.id = d.routeId ' . $clause . '
             ORDER BY d.preparedAt DESC, d.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateDelivery'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function delivery(int $id): array
    {
        $row = $this->db->first(
            'SELECT d.*, c.name AS customerName, c.code AS customerCode, c.address AS customerAddress,
                    c.wilaya AS customerWilaya, c.phone AS customerPhone, o.orderNumber, o.totalTtc AS orderTotal,
                    r.routeNumber
             FROM deliveryNotes d
             JOIN customers c ON c.id = d.customerId
             JOIN salesOrders o ON o.id = d.orderId
             LEFT JOIN deliveryRoutes r ON r.id = d.routeId
             WHERE d.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Delivery note');
        }

        $delivery = $this->hydrateDelivery($row);
        $delivery['items'] = $this->db->select(
            'SELECT di.id, di.productId, p.sku AS productCode, p.nameFr AS productName, p.unit,
                    di.productBatchId, b.batchNumber, b.expiryDate, di.quantity
             FROM deliveryItems di
             JOIN products p ON p.id = di.productId
             JOIN productBatches b ON b.id = di.productBatchId
             WHERE di.deliveryNoteId = :id ORDER BY di.id',
            ['id' => $id]
        );

        return $delivery;
    }

    /**
     * Prepares the delivery: reserves the exact batches FEFO, writes the OUT movements and advances
     * the order lines' delivered quantity. Nothing leaves the building yet — the note is PREPARED
     * until it is signed for.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function createDelivery(int $orderId, array $data, int $userId): array
    {
        $order = $this->order($orderId);
        if (!in_array($order['status'], ['CONFIRMED', 'RESERVED', 'PARTIAL'], true)) {
            throw $this->wrongStatus('Sales order', $order['status'], ['CONFIRMED', 'RESERVED', 'PARTIAL']);
        }

        /** @var array<int,float> $wanted keyed by order item id */
        $wanted = [];
        if (!empty($data['items']) && is_array($data['items'])) {
            foreach ($data['items'] as $line) {
                $itemId = (int) ($line['orderItemId'] ?? 0);
                $quantity = round((float) ($line['quantity'] ?? 0), 3);
                if ($quantity <= 0) {
                    continue;
                }
                $wanted[$itemId] = ($wanted[$itemId] ?? 0) + $quantity;
            }
        } else {
            foreach ($order['items'] as $item) {
                $remaining = round((float) $item['quantity'] - (float) $item['deliveredQty'], 3);
                if ($remaining > 0) {
                    $wanted[(int) $item['id']] = $remaining;
                }
            }
        }
        if ($wanted === []) {
            throw ApiException::conflict('Every line of this order is already delivered.', 'NOTHING_TO_DELIVER');
        }

        $itemsById = [];
        foreach ($order['items'] as $item) {
            $itemsById[(int) $item['id']] = $item;
        }

        $deliveryId = $this->db->transaction(function () use ($order, $wanted, $itemsById, $data, $userId): int {
            $noteNumber = $this->nextNumber('deliveryNotes', 'noteNumber', 'BL');
            $this->db->execute(
                'INSERT INTO deliveryNotes (noteNumber, orderId, customerId, status, preparedAt, notes)
                 VALUES (:number, :orderId, :customerId, \'PREPARED\', NOW(), :notes)',
                [
                    'number' => $noteNumber,
                    'orderId' => $order['id'],
                    'customerId' => $order['customerId'],
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                ]
            );
            $noteId = $this->db->lastId();

            foreach ($wanted as $itemId => $quantity) {
                $item = $itemsById[$itemId] ?? null;
                if ($item === null) {
                    throw ApiException::validation(['items' => 'validation.exists']);
                }
                $remaining = round((float) $item['quantity'] - (float) $item['deliveredQty'], 3);
                if ($quantity > $remaining + 0.0001) {
                    throw ApiException::conflict(
                        'A delivery cannot exceed what remains to ship on the line.',
                        'DELIVERY_EXCEEDS_ORDER',
                        ['orderItemId' => $itemId, 'remaining' => $remaining, 'requested' => $quantity]
                    );
                }

                $allocations = $this->allocate((int) $item['productId'], $quantity);
                foreach ($allocations as $allocation) {
                    $this->db->execute(
                        'INSERT INTO deliveryItems (deliveryNoteId, productId, productBatchId, quantity)
                         VALUES (:noteId, :productId, :batchId, :quantity)',
                        [
                            'noteId' => $noteId,
                            'productId' => (int) $item['productId'],
                            'batchId' => $allocation['batchId'],
                            'quantity' => $allocation['quantity'],
                        ]
                    );
                    $this->moveOut($allocation, $noteId, $noteNumber, $userId,
                        'Livraison ' . $noteNumber . ' - ' . $order['customerName']);
                }

                $this->db->execute(
                    'UPDATE salesOrderItems SET deliveredQty = deliveredQty + :quantity WHERE id = :id',
                    ['quantity' => $quantity, 'id' => $itemId]
                );
            }

            $this->db->execute(
                'UPDATE salesOrders SET status = CASE WHEN status = \'CONFIRMED\' THEN \'RESERVED\' ELSE status END,
                        updatedBy = :userId WHERE id = :id',
                ['userId' => $userId, 'id' => $order['id']]
            );

            return $noteId;
        });

        return $this->delivery($deliveryId);
    }

    /** @return array<string,mixed> */
    public function loadDelivery(int $id): array
    {
        $delivery = $this->delivery($id);
        if ($delivery['status'] !== 'PREPARED') {
            throw $this->wrongStatus('Delivery note', $delivery['status'], ['PREPARED']);
        }
        $this->db->execute('UPDATE deliveryNotes SET status = \'LOADED\' WHERE id = :id', ['id' => $id]);

        return $this->delivery($id);
    }

    /**
     * The customer signs. This is the moment the order becomes PARTIAL: the goods are gone but not
     * invoiced yet.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function deliverDelivery(int $id, array $data, int $userId): array
    {
        $delivery = $this->delivery($id);
        if (!in_array($delivery['status'], ['PREPARED', 'LOADED', 'IN_TRANSIT'], true)) {
            throw $this->wrongStatus('Delivery note', $delivery['status'], ['PREPARED', 'LOADED', 'IN_TRANSIT']);
        }
        $signedBy = trim((string) ($data['signedBy'] ?? ''));
        if ($signedBy === '') {
            throw ApiException::validation(['signedBy' => 'validation.required']);
        }

        $this->db->transaction(function () use ($id, $delivery, $signedBy, $data, $userId): void {
            $this->db->execute(
                'UPDATE deliveryNotes SET status = \'DELIVERED\', deliveredAt = NOW(), signedBy = :signedBy,
                        signaturePath = :signaturePath, notes = :notes WHERE id = :id',
                [
                    'signedBy' => mb_substr($signedBy, 0, 120),
                    'signaturePath' => $this->path($data['signaturePath'] ?? null),
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? $delivery['notes'])), 0, 255),
                    'id' => $id,
                ]
            );
            $this->syncOrderAfterDelivery((int) $delivery['orderId'], $userId);
        });

        return $this->delivery($id);
    }

    /**
     * A returned note puts the exact batches back and reverses the delivered quantities; the note
     * itself stays in the history as RETURNED.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function returnDelivery(int $id, array $data, int $userId): array
    {
        $delivery = $this->delivery($id);
        if (!in_array($delivery['status'], ['DELIVERED', 'IN_TRANSIT', 'LOADED'], true)) {
            throw $this->wrongStatus('Delivery note', $delivery['status'], ['DELIVERED', 'IN_TRANSIT', 'LOADED']);
        }

        $this->db->transaction(function () use ($id, $delivery, $data, $userId): void {
            foreach ($delivery['items'] as $item) {
                $this->db->execute(
                    'UPDATE productBatches SET quantity = quantity + :quantity WHERE id = :batchId',
                    ['quantity' => (float) $item['quantity'], 'batchId' => (int) $item['productBatchId']]
                );
                $this->db->execute(
                    'INSERT INTO stockMovements (movementNumber, movementType, warehouseId, productBatchId, quantity,
                            unitCost, referenceType, referenceId, reason, userId, occurredAt)
                     SELECT :number, \'RETURN\', b.warehouseId, b.id, :quantity, b.unitCost, \'DELIVERY_RETURN\',
                            :noteId, :reason, :userId, NOW()
                     FROM productBatches b WHERE b.id = :batchId',
                    [
                        'number' => $this->nextNumber('stockMovements', 'movementNumber', 'MVT'),
                        'quantity' => (float) $item['quantity'],
                        'noteId' => $id,
                        'reason' => mb_substr(trim((string) ($data['reason'] ?? 'Retour client')), 0, 255),
                        'userId' => $userId,
                        'batchId' => (int) $item['productBatchId'],
                    ]
                );
            }

            $this->db->execute(
                'UPDATE deliveryNotes SET status = \'RETURNED\', notes = :notes WHERE id = :id',
                [
                    'notes' => mb_substr(trim((string) ($data['reason'] ?? 'Retour client')), 0, 255),
                    'id' => $id,
                ]
            );

            // Give the shipped quantity back to the order line, product by product.
            $shipped = [];
            foreach ($delivery['items'] as $item) {
                $shipped[(int) $item['productId']] = ($shipped[(int) $item['productId']] ?? 0.0)
                    + (float) $item['quantity'];
            }
            foreach ($this->db->select('SELECT id, productId, deliveredQty FROM salesOrderItems WHERE orderId = :id',
                ['id' => $delivery['orderId']]) as $line) {
                $productId = (int) $line['productId'];
                if (!isset($shipped[$productId])) {
                    continue;
                }
                $quantity = min($shipped[$productId], (float) $line['deliveredQty']);
                $shipped[$productId] -= $quantity;
                $this->db->execute(
                    'UPDATE salesOrderItems SET deliveredQty = GREATEST(deliveredQty - :quantity, 0) WHERE id = :id',
                    ['quantity' => $quantity, 'id' => (int) $line['id']]
                );
            }

            $this->syncOrderAfterDelivery((int) $delivery['orderId'], $userId);
        });

        return $this->delivery($id);
    }

    // -------------------------------------------------------------------- invoices

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateInvoices(int $page, int $pageSize, string $search, ?string $status,
                                     ?int $customerId, ?string $from, ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(i.invoiceNumber LIKE :searchNumber OR c.name LIKE :searchCustomer OR c.code LIKE :searchCode)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchCustomer'] = $term;
            $params['searchCode'] = $term;
        }
        if ($status !== null && in_array(strtoupper($status), self::INVOICE_STATUSES, true)) {
            $where[] = 'i.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($customerId !== null && $customerId > 0) {
            $where[] = 'i.customerId = :customerId';
            $params['customerId'] = $customerId;
        }
        if ($from !== null && $from !== '') {
            $where[] = 'i.invoiceDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'i.invoiceDate <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM invoices i JOIN customers c ON c.id = i.customerId ' . $clause, $params);
        $summary = $this->db->first(
            'SELECT COALESCE(SUM(i.totalTtc), 0) AS totalTtc, COALESCE(SUM(i.paidAmount), 0) AS paidAmount,
                    COALESCE(SUM(i.totalTtc - i.paidAmount), 0) AS outstanding
             FROM invoices i JOIN customers c ON c.id = i.customerId ' . $clause
            . ' AND i.status <> \'CANCELLED\' AND i.status <> \'DRAFT\'',
            $params
        );

        $rows = $this->db->select(
            'SELECT i.*, c.name AS customerName, c.code AS customerCode, o.orderNumber,
                    (i.totalTtc - i.paidAmount) AS remaining,
                    CASE WHEN i.dueDate IS NOT NULL AND i.status IN (\'ISSUED\', \'PARTIAL\')
                              AND i.dueDate < CURDATE() THEN 1 ELSE 0 END AS isOverdue,
                    (SELECT COUNT(*) FROM invoiceItems ii WHERE ii.invoiceId = i.id) AS lineCount
             FROM invoices i
             JOIN customers c ON c.id = i.customerId
             LEFT JOIN salesOrders o ON o.id = i.orderId ' . $clause . '
             ORDER BY i.invoiceDate DESC, i.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return [
            'rows' => array_map([$this, 'hydrateInvoice'], $rows),
            'total' => $total,
            'summary' => [
                'totalTtc' => round((float) ($summary['totalTtc'] ?? 0), 2),
                'paidAmount' => round((float) ($summary['paidAmount'] ?? 0), 2),
                'outstanding' => round((float) ($summary['outstanding'] ?? 0), 2),
            ],
        ];
    }

    /** @return array<string,mixed> */
    public function invoice(int $id): array
    {
        $row = $this->db->first(
            'SELECT i.*, c.name AS customerName, c.code AS customerCode, c.address AS customerAddress,
                    c.wilaya AS customerWilaya, c.phone AS customerPhone, c.email AS customerEmail,
                    o.orderNumber, (i.totalTtc - i.paidAmount) AS remaining,
                    CASE WHEN i.dueDate IS NOT NULL AND i.status IN (\'ISSUED\', \'PARTIAL\')
                              AND i.dueDate < CURDATE() THEN 1 ELSE 0 END AS isOverdue
             FROM invoices i
             JOIN customers c ON c.id = i.customerId
             LEFT JOIN salesOrders o ON o.id = i.orderId
             WHERE i.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Invoice');
        }

        $invoice = $this->hydrateInvoice($row);
        $invoice['items'] = $this->db->select(
            'SELECT ii.id, ii.productId, p.sku AS productCode, p.nameFr AS productName, p.nameAr AS productNameAr,
                    p.unit, ii.productBatchId, b.batchNumber, ii.quantity, ii.unitPrice, ii.discountPct,
                    ii.vatRate, ii.lineTotal
             FROM invoiceItems ii
             JOIN products p ON p.id = ii.productId
             LEFT JOIN productBatches b ON b.id = ii.productBatchId
             WHERE ii.invoiceId = :id ORDER BY ii.id',
            ['id' => $id]
        );
        $invoice['payments'] = $this->db->select(
            'SELECT p.id, p.paymentNumber, p.amount, p.paidAt, p.method, p.reference, u.fullName AS userName
             FROM payments p LEFT JOIN users u ON u.id = p.userId
             WHERE p.invoiceId = :id ORDER BY p.paidAt, p.id',
            ['id' => $id]
        );
        $invoice['creditNotes'] = $this->db->select(
            'SELECT id, creditNumber, creditDate, reason, amountHt, vatAmount, totalTtc, status
             FROM creditNotes WHERE invoiceId = :id ORDER BY id',
            ['id' => $id]
        );

        return $invoice;
    }

    /**
     * Drafts an invoice. With an order it bills exactly what was delivered and not yet invoiced; for a
     * counter sale it takes its own lines.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function createInvoice(array $data, int $userId): array
    {
        $orderId = (int) ($data['orderId'] ?? 0);
        $order = null;
        $lines = [];

        if ($orderId > 0) {
            $order = $this->order($orderId);
            if (in_array($order['status'], ['DRAFT', 'CANCELLED'], true)) {
                throw $this->wrongStatus('Sales order', $order['status'], ['CONFIRMED', 'RESERVED', 'PARTIAL', 'INVOICED']);
            }
            foreach ($order['items'] as $item) {
                $billable = round((float) $item['deliveredQty'] - (float) $item['invoicedQty'], 3);
                if ($billable <= 0) {
                    continue;
                }
                $lines[] = $this->line(
                    (int) $item['productId'],
                    $billable,
                    (float) $item['unitPrice'],
                    (float) $item['discountPct'],
                    (float) $item['vatRate'],
                    $this->deliveredBatch($orderId, (int) $item['productId'])
                );
            }
            if ($lines === []) {
                throw ApiException::conflict('Nothing is delivered yet on this order: prepare a delivery note first.',
                    'NOTHING_TO_INVOICE');
            }
            $customerId = (int) $order['customerId'];
        } else {
            $customerId = (int) ($data['customerId'] ?? 0);
            $customer = $this->customer($customerId);
            $lines = $this->lines($data, (string) $customer['priceSegment']);
        }

        [$amountHt, $vatAmount] = $this->totals($lines);
        $totalTtc = round($amountHt + $vatAmount, 2);

        $id = $this->db->transaction(function () use ($data, $customerId, $orderId, $lines, $amountHt, $vatAmount,
            $totalTtc, $userId): int {
            $this->db->execute(
                'INSERT INTO invoices (invoiceNumber, customerId, orderId, invoiceDate, dueDate, amountHt, vatAmount,
                        totalTtc, paidAmount, status, currency, notes, createdBy, updatedBy)
                 VALUES (:number, :customerId, :orderId, :invoiceDate, :dueDate, :amountHt, :vatAmount, :totalTtc,
                        0, \'DRAFT\', :currency, :notes, :createdBy, :updatedBy)',
                [
                    'number' => $this->nextNumber('invoices', 'invoiceNumber', 'FA'),
                    'customerId' => $customerId,
                    'orderId' => $orderId > 0 ? $orderId : null,
                    'invoiceDate' => $this->date($data['invoiceDate'] ?? null) ?? date('Y-m-d'),
                    'dueDate' => $this->date($data['dueDate'] ?? null) ?? date('Y-m-d', strtotime('+30 days')),
                    'amountHt' => $amountHt,
                    'vatAmount' => $vatAmount,
                    'totalTtc' => $totalTtc,
                    'currency' => (string) ($data['currency'] ?? 'DZD'),
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                    'createdBy' => $userId,
                    'updatedBy' => $userId,
                ]
            );
            $invoiceId = $this->db->lastId();
            $this->insertInvoiceItems($invoiceId, $lines);

            return $invoiceId;
        });

        return $this->invoice($id);
    }

    /**
     * A draft invoice can still be corrected. Once issued it is frozen — the database trigger enforces
     * it as well, so this check is the friendly message, not the guarantee.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function updateInvoice(int $id, array $data, int $userId): array
    {
        $invoice = $this->invoice($id);
        if ($invoice['status'] !== 'DRAFT') {
            throw ApiException::conflict(
                'This invoice is issued and can no longer be modified: raise a credit note instead.',
                'INVOICE_FINALIZED',
                ['invoiceNumber' => $invoice['invoiceNumber'], 'finalizedAt' => $invoice['finalizedAt']]
            );
        }

        $lines = isset($data['items']) && is_array($data['items'])
            ? $this->lines($data, $this->customer((int) $invoice['customerId'])['priceSegment'])
            : null;
        [$amountHt, $vatAmount] = $lines === null
            ? [(float) $invoice['amountHt'], (float) $invoice['vatAmount']]
            : $this->totals($lines);

        $this->db->transaction(function () use ($id, $invoice, $data, $lines, $amountHt, $vatAmount, $userId): void {
            $this->db->execute(
                'UPDATE invoices SET invoiceDate = :invoiceDate, dueDate = :dueDate, amountHt = :amountHt,
                        vatAmount = :vatAmount, totalTtc = :totalTtc, notes = :notes, updatedBy = :userId
                 WHERE id = :id',
                [
                    'invoiceDate' => $this->date($data['invoiceDate'] ?? null) ?? $invoice['invoiceDate'],
                    'dueDate' => array_key_exists('dueDate', $data) ? $this->date($data['dueDate']) : $invoice['dueDate'],
                    'amountHt' => $amountHt,
                    'vatAmount' => $vatAmount,
                    'totalTtc' => round($amountHt + $vatAmount, 2),
                    'notes' => array_key_exists('notes', $data)
                        ? mb_substr(trim((string) $data['notes']), 0, 255) : (string) $invoice['notes'],
                    'userId' => $userId,
                    'id' => $id,
                ]
            );
            if ($lines !== null) {
                $this->db->execute('DELETE FROM invoiceItems WHERE invoiceId = :id', ['id' => $id]);
                $this->insertInvoiceItems($id, $lines);
            }
        });

        return $this->invoice($id);
    }

    /**
     * Issuing freezes the document, books the receivable on the customer and advances the order's
     * invoiced quantities — all in one transaction.
     *
     * @return array<string,mixed>
     */
    public function issueInvoice(int $id, int $userId): array
    {
        $invoice = $this->invoice($id);
        if ($invoice['status'] !== 'DRAFT') {
            throw $this->wrongStatus('Invoice', $invoice['status'], ['DRAFT']);
        }
        if (empty($invoice['items'])) {
            throw ApiException::conflict('An invoice without lines cannot be issued.', 'INVOICE_EMPTY');
        }
        $this->creditGuard((int) $invoice['customerId'], (float) $invoice['totalTtc'], 'invoice');

        $this->db->transaction(function () use ($id, $invoice, $userId): void {
            $this->db->execute(
                'UPDATE invoices SET status = \'ISSUED\', finalizedAt = NOW(), updatedBy = :userId WHERE id = :id',
                ['userId' => $userId, 'id' => $id]
            );
            $this->db->execute(
                'UPDATE customers SET balance = balance + :amount, updatedBy = :userId WHERE id = :customerId',
                ['amount' => (float) $invoice['totalTtc'], 'userId' => $userId,
                    'customerId' => (int) $invoice['customerId']]
            );

            if ($invoice['orderId'] !== null) {
                foreach ($invoice['items'] as $item) {
                    $this->db->execute(
                        'UPDATE salesOrderItems SET invoicedQty = LEAST(invoicedQty + :quantity, quantity)
                         WHERE orderId = :orderId AND productId = :productId',
                        [
                            'quantity' => (float) $item['quantity'],
                            'orderId' => (int) $invoice['orderId'],
                            'productId' => (int) $item['productId'],
                        ]
                    );
                }
                $this->syncOrderAfterDelivery((int) $invoice['orderId'], $userId);
            }
        });

        return $this->invoice($id);
    }

    /** @return array<string,mixed> */
    public function cancelInvoice(int $id, int $userId): array
    {
        $invoice = $this->invoice($id);
        if ($invoice['status'] === 'CANCELLED') {
            return $invoice;
        }
        if ($invoice['status'] !== 'DRAFT') {
            throw ApiException::conflict(
                'An issued invoice can only be corrected by a credit note.', 'INVOICE_FINALIZED');
        }
        $this->db->execute('UPDATE invoices SET status = \'CANCELLED\', updatedBy = :userId WHERE id = :id',
            ['userId' => $userId, 'id' => $id]);

        return $this->invoice($id);
    }

    /** Counts a print so the company knows how many copies of an invoice are in circulation. */
    public function registerPrint(int $id): void
    {
        $this->db->execute('UPDATE invoices SET printedCount = printedCount + 1 WHERE id = :id', ['id' => $id]);
    }

    // -------------------------------------------------------------------- payments

    /** @return array{rows:array<int,array>,total:int} */
    public function paginatePayments(int $page, int $pageSize, ?int $customerId, ?int $invoiceId, string $search,
                                     ?string $method): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($search !== '') {
            $where[] = '(p.paymentNumber LIKE :searchNumber OR c.name LIKE :searchCustomer
                         OR p.reference LIKE :searchReference)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchCustomer'] = $term;
            $params['searchReference'] = $term;
        }
        if ($customerId !== null && $customerId > 0) {
            $where[] = 'p.customerId = :customerId';
            $params['customerId'] = $customerId;
        }
        if ($invoiceId !== null && $invoiceId > 0) {
            $where[] = 'p.invoiceId = :invoiceId';
            $params['invoiceId'] = $invoiceId;
        }
        if ($method !== null && in_array(strtoupper($method), self::PAYMENT_METHODS, true)) {
            $where[] = 'p.method = :method';
            $params['method'] = strtoupper($method);
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM payments p JOIN customers c ON c.id = p.customerId ' . $clause, $params);
        $sum = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(p.amount), 0) FROM payments p JOIN customers c ON c.id = p.customerId ' . $clause,
            $params);

        $rows = $this->db->select(
            'SELECT p.*, c.name AS customerName, c.code AS customerCode, i.invoiceNumber,
                    a.name AS cashAccountName, u.fullName AS userName
             FROM payments p
             JOIN customers c ON c.id = p.customerId
             LEFT JOIN invoices i ON i.id = p.invoiceId
             LEFT JOIN cashAccounts a ON a.id = p.cashAccountId
             LEFT JOIN users u ON u.id = p.userId ' . $clause . '
             ORDER BY p.paidAt DESC, p.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return [
            'rows' => array_map([$this, 'hydratePayment'], $rows),
            'total' => $total,
            'summary' => ['amount' => round($sum, 2)],
        ];
    }

    /**
     * Records a settlement. Against an invoice it may not exceed what remains; without an invoice it
     * is an on-account payment that simply lowers the customer's balance.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function createPayment(array $data, int $userId): array
    {
        $amount = round((float) ($data['amount'] ?? 0), 2);
        if ($amount <= 0) {
            throw ApiException::validation(['amount' => 'validation.positive']);
        }
        $method = strtoupper(trim((string) ($data['method'] ?? 'CASH')));
        if (!in_array($method, self::PAYMENT_METHODS, true)) {
            throw ApiException::validation(['method' => 'validation.allowed_values']);
        }
        $cashAccountId = (int) ($data['cashAccountId'] ?? 0);
        if ($cashAccountId <= 0) {
            throw ApiException::validation(['cashAccountId' => 'validation.required']);
        }
        if ($this->db->first('SELECT id FROM cashAccounts WHERE id = :id', ['id' => $cashAccountId]) === null) {
            throw ApiException::validation(['cashAccountId' => 'validation.exists']);
        }

        $invoiceId = (int) ($data['invoiceId'] ?? 0);
        $invoice = null;
        if ($invoiceId > 0) {
            $invoice = $this->invoice($invoiceId);
            if (!in_array($invoice['status'], ['ISSUED', 'PARTIAL'], true)) {
                throw ApiException::conflict(
                    'Only an issued invoice that is not fully paid can receive a payment.',
                    'INVOICE_NOT_PAYABLE',
                    ['status' => $invoice['status']]
                );
            }
            $remaining = round((float) $invoice['totalTtc'] - (float) $invoice['paidAmount'], 2);
            if ($amount > $remaining + 0.001) {
                throw ApiException::conflict(
                    'The payment exceeds what is still due on this invoice.',
                    'PAYMENT_EXCEEDS_BALANCE',
                    ['remaining' => $remaining, 'amount' => $amount]
                );
            }
            $customerId = (int) $invoice['customerId'];
        } else {
            $customerId = (int) ($data['customerId'] ?? 0);
            $this->customer($customerId);
        }

        $id = $this->db->transaction(function () use ($data, $amount, $method, $cashAccountId, $invoiceId, $invoice,
            $customerId, $userId): int {
            $this->db->execute(
                'INSERT INTO payments (paymentNumber, customerId, invoiceId, cashAccountId, amount, paidAt, method,
                        reference, userId, notes)
                 VALUES (:number, :customerId, :invoiceId, :cashAccountId, :amount, :paidAt, :method, :reference,
                        :userId, :notes)',
                [
                    'number' => $this->nextNumber('payments', 'paymentNumber', 'ENC'),
                    'customerId' => $customerId,
                    'invoiceId' => $invoiceId > 0 ? $invoiceId : null,
                    'cashAccountId' => $cashAccountId,
                    'amount' => $amount,
                    'paidAt' => $this->dateTime($data['paidAt'] ?? null) ?? date('Y-m-d H:i:s'),
                    'method' => $method,
                    'reference' => mb_substr(trim((string) ($data['reference'] ?? '')), 0, 60),
                    'userId' => $userId,
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                ]
            );
            $paymentId = $this->db->lastId();

            $this->db->execute(
                'UPDATE customers SET balance = balance - :amount, updatedBy = :userId WHERE id = :customerId',
                ['amount' => $amount, 'userId' => $userId, 'customerId' => $customerId]
            );

            if ($invoice !== null) {
                // The status is assigned before the amount on purpose: MySQL applies the
                // assignments left to right, so the CASE must still see the previous paidAmount.
                $this->db->execute(
                    'UPDATE invoices
                     SET status = CASE WHEN paidAmount + :amount >= totalTtc THEN \'PAID\' ELSE \'PARTIAL\' END,
                         paidAmount = paidAmount + :amount2,
                         updatedBy = :userId
                     WHERE id = :id',
                    [
                        'amount' => $amount,
                        'amount2' => $amount,
                        'userId' => $userId,
                        'id' => $invoiceId,
                    ]
                );
            }

            return $paymentId;
        });

        return $this->payment($id);
    }

    /** @return array<string,mixed> */
    public function payment(int $id): array
    {
        $row = $this->db->first(
            'SELECT p.*, c.name AS customerName, c.code AS customerCode, i.invoiceNumber, i.totalTtc AS invoiceTotal,
                    a.name AS cashAccountName, u.fullName AS userName
             FROM payments p
             JOIN customers c ON c.id = p.customerId
             LEFT JOIN invoices i ON i.id = p.invoiceId
             LEFT JOIN cashAccounts a ON a.id = p.cashAccountId
             LEFT JOIN users u ON u.id = p.userId
             WHERE p.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Payment');
        }

        return $this->hydratePayment($row) + ['notes' => (string) $row['notes']];
    }

    /** @return array<int,array> */
    public function cashAccounts(): array
    {
        return $this->db->select(
            'SELECT id, code, name, type, bankName, openingBalance FROM cashAccounts WHERE isActive = 1 ORDER BY name'
        );
    }

    // ---------------------------------------------------------------- credit notes

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateCreditNotes(int $page, int $pageSize, ?int $customerId, ?string $status): array
    {
        $where = ['1 = 1'];
        $params = [];
        if ($customerId !== null && $customerId > 0) {
            $where[] = 'cn.customerId = :customerId';
            $params['customerId'] = $customerId;
        }
        if ($status !== null && in_array(strtoupper($status), ['DRAFT', 'ISSUED', 'APPLIED', 'CANCELLED'], true)) {
            $where[] = 'cn.status = :status';
            $params['status'] = strtoupper($status);
        }
        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM creditNotes cn ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT cn.*, c.name AS customerName, i.invoiceNumber
             FROM creditNotes cn
             JOIN customers c ON c.id = cn.customerId
             JOIN invoices i ON i.id = cn.invoiceId ' . $clause . '
             ORDER BY cn.creditDate DESC, cn.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateCreditNote'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function creditNote(int $id): array
    {
        $row = $this->db->first(
            'SELECT cn.*, c.name AS customerName, c.code AS customerCode, i.invoiceNumber, i.totalTtc AS invoiceTotal
             FROM creditNotes cn
             JOIN customers c ON c.id = cn.customerId
             JOIN invoices i ON i.id = cn.invoiceId
             WHERE cn.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Credit note');
        }
        $note = $this->hydrateCreditNote($row);
        $note['items'] = $this->db->select(
            'SELECT ci.id, ci.productId, p.sku AS productCode, p.nameFr AS productName, p.unit, ci.quantity,
                    ci.unitPrice, ci.vatRate, ci.lineTotal
             FROM creditNoteItems ci JOIN products p ON p.id = ci.productId
             WHERE ci.creditNoteId = :id ORDER BY ci.id',
            ['id' => $id]
        );

        return $note;
    }

    /**
     * A credit note is the only way to correct an issued invoice. It may not credit more than what is
     * still due on the invoice.
     *
     * @param array<string,mixed> $data @return array<string,mixed>
     */
    public function createCreditNote(int $invoiceId, array $data, int $userId): array
    {
        $invoice = $this->invoice($invoiceId);
        if (!in_array($invoice['status'], ['ISSUED', 'PARTIAL', 'PAID'], true)) {
            throw ApiException::conflict('Only an issued invoice can be corrected by a credit note.',
                'INVOICE_NOT_ISSUED', ['status' => $invoice['status']]);
        }
        $reason = trim((string) ($data['reason'] ?? ''));
        if ($reason === '') {
            throw ApiException::validation(['reason' => 'validation.required']);
        }

        $remaining = round((float) $invoice['totalTtc'] - (float) $invoice['paidAmount'], 2);
        $items = [];
        if (isset($data['items']) && is_array($data['items']) && $data['items'] !== []) {
            $items = $this->lines($data, 'RETAIL');
            [$amountHt, $vatAmount] = $this->totals($items);
        } else {
            $amountHt = round((float) ($data['amountHt'] ?? 0), 2);
            if ($amountHt <= 0) {
                throw ApiException::validation(['amountHt' => 'validation.positive']);
            }
            $vatRate = round((float) ($data['vatRate'] ?? 19), 4);
            $vatAmount = round($amountHt * $vatRate / 100, 2);
        }
        $totalTtc = round($amountHt + $vatAmount, 2);
        if ($totalTtc > $remaining + 0.001) {
            throw ApiException::conflict(
                'The credit note exceeds what is still due on the invoice.',
                'CREDIT_EXCEEDS_BALANCE',
                ['remaining' => $remaining, 'amount' => $totalTtc]
            );
        }

        $id = $this->db->transaction(function () use ($invoiceId, $invoice, $data, $reason, $items, $amountHt,
            $vatAmount, $totalTtc, $userId): int {
            $this->db->execute(
                'INSERT INTO creditNotes (creditNumber, invoiceId, customerId, creditDate, reason, amountHt,
                        vatAmount, totalTtc, status, createdBy)
                 VALUES (:number, :invoiceId, :customerId, :creditDate, :reason, :amountHt, :vatAmount, :totalTtc,
                        \'ISSUED\', :userId)',
                [
                    'number' => $this->nextNumber('creditNotes', 'creditNumber', 'AV'),
                    'invoiceId' => $invoiceId,
                    'customerId' => (int) $invoice['customerId'],
                    'creditDate' => $this->date($data['creditDate'] ?? null) ?? date('Y-m-d'),
                    'reason' => mb_substr($reason, 0, 255),
                    'amountHt' => $amountHt,
                    'vatAmount' => $vatAmount,
                    'totalTtc' => $totalTtc,
                    'userId' => $userId,
                ]
            );
            $noteId = $this->db->lastId();
            foreach ($items as $item) {
                $this->db->execute(
                    'INSERT INTO creditNoteItems (creditNoteId, productId, quantity, unitPrice, vatRate, lineTotal)
                     VALUES (:noteId, :productId, :quantity, :unitPrice, :vatRate, :lineTotal)',
                    [
                        'noteId' => $noteId,
                        'productId' => $item['productId'],
                        'quantity' => $item['quantity'],
                        'unitPrice' => $item['unitPrice'],
                        'vatRate' => $item['vatRate'],
                        'lineTotal' => $item['lineTotal'],
                    ]
                );
            }

            return $noteId;
        });

        return $this->creditNote($id);
    }

    /**
     * Applying the note is the accounting event: the receivable shrinks and the invoice it corrects
     * carries the credit in its paid amount.
     *
     * @return array<string,mixed>
     */
    public function applyCreditNote(int $id, int $userId): array
    {
        $note = $this->creditNote($id);
        if ($note['status'] !== 'ISSUED') {
            throw $this->wrongStatus('Credit note', $note['status'], ['ISSUED']);
        }

        $this->db->transaction(function () use ($id, $note, $userId): void {
            $this->db->execute('UPDATE creditNotes SET status = \'APPLIED\' WHERE id = :id', ['id' => $id]);
            $this->db->execute(
                'UPDATE customers SET balance = balance - :amount, updatedBy = :userId WHERE id = :customerId',
                ['amount' => (float) $note['totalTtc'], 'userId' => $userId,
                    'customerId' => (int) $note['customerId']]
            );
            $this->db->execute(
                'UPDATE invoices
                 SET status = CASE WHEN paidAmount + :amount >= totalTtc THEN \'PAID\' ELSE \'PARTIAL\' END,
                     paidAmount = paidAmount + :amount2,
                     updatedBy = :userId
                 WHERE id = :id AND status IN (\'ISSUED\', \'PARTIAL\')',
                [
                    'amount' => (float) $note['totalTtc'],
                    'amount2' => (float) $note['totalTtc'],
                    'userId' => $userId,
                    'id' => (int) $note['invoiceId'],
                ]
            );
        });

        return $this->creditNote($id);
    }

    /** @return array<string,mixed> */
    public function cancelCreditNote(int $id): array
    {
        $note = $this->creditNote($id);
        if ($note['status'] !== 'ISSUED') {
            throw $this->wrongStatus('Credit note', $note['status'], ['ISSUED']);
        }
        $this->db->execute('UPDATE creditNotes SET status = \'CANCELLED\' WHERE id = :id', ['id' => $id]);

        return $this->creditNote($id);
    }

    // ------------------------------------------------------------------ documents

    /**
     * Everything a printed document needs, in the language asked for.
     *
     * @return array<string,mixed>
     */
    public function document(string $type, int $id, string $lang): array
    {
        return match ($type) {
            'quotation' => $this->quotationDocument($this->quotation($id), $lang),
            'order' => $this->orderDocument($this->order($id), $lang),
            'delivery' => $this->deliveryDocument($this->delivery($id), $lang),
            'invoice' => $this->invoiceDocument($this->invoice($id), $lang),
            'statement' => $this->statementDocument($this->customers->statement($id, null, null), $lang),
            default => throw ApiException::notFound('Document'),
        };
    }

    /** @param array<string,mixed> $quotation @return array<string,mixed> */
    private function quotationDocument(array $quotation, string $lang): array
    {
        return [
            'type' => 'QUOTATION',
            'lang' => $lang,
            'number' => $quotation['quoteNumber'],
            'date' => $quotation['quoteDate'],
            'secondaryDateLabel' => 'validUntil',
            'secondaryDate' => $quotation['validUntil'],
            'status' => $quotation['status'],
            'customer' => [
                'code' => $quotation['customerCode'],
                'name' => $quotation['customerName'],
                'address' => $quotation['customerAddress'],
                'wilaya' => $quotation['customerWilaya'],
                'phone' => $quotation['customerPhone'],
            ],
            'items' => array_map(static fn (array $item): array => [
                'productCode' => $item['productCode'],
                'productName' => $item['productName'],
                'unit' => $item['unit'],
                'quantity' => (float) $item['quantity'],
                'unitPrice' => (float) $item['unitPrice'],
                'discountPct' => (float) $item['discountPct'],
                'vatRate' => (float) $item['vatRate'],
                'lineTotal' => (float) $item['lineTotal'],
                'batchNumber' => null,
            ], $quotation['items']),
            'amountHt' => (float) $quotation['amountHt'],
            'vatAmount' => (float) $quotation['vatAmount'],
            'totalTtc' => (float) $quotation['totalTtc'],
            'paidAmount' => 0.0,
            'notes' => (string) $quotation['notes'],
            'reference' => null,
            'extra' => ['representative' => $quotation['representativeName']],
        ];
    }

    /** @param array<string,mixed> $order @return array<string,mixed> */
    private function orderDocument(array $order, string $lang): array
    {
        return [
            'type' => 'ORDER',
            'lang' => $lang,
            'number' => $order['orderNumber'],
            'date' => $order['orderDate'],
            'secondaryDateLabel' => 'deliveryDate',
            'secondaryDate' => $order['deliveryDate'],
            'status' => $order['status'],
            'customer' => [
                'code' => $order['customerCode'],
                'name' => $order['customerName'],
                'address' => $order['customerAddress'],
                'wilaya' => $order['customerWilaya'],
                'phone' => $order['customerPhone'],
            ],
            'items' => array_map(static fn (array $item): array => [
                'productCode' => $item['productCode'],
                'productName' => $item['productName'],
                'unit' => $item['unit'],
                'quantity' => (float) $item['quantity'],
                'unitPrice' => (float) $item['unitPrice'],
                'discountPct' => (float) $item['discountPct'],
                'vatRate' => (float) $item['vatRate'],
                'lineTotal' => (float) $item['lineTotal'],
                'batchNumber' => null,
            ], $order['items']),
            'amountHt' => (float) $order['amountHt'],
            'vatAmount' => (float) $order['vatAmount'],
            'totalTtc' => (float) $order['totalTtc'],
            'paidAmount' => 0.0,
            'notes' => (string) $order['notes'],
            'reference' => $order['quoteNumber'],
            'extra' => ['representative' => $order['representativeName']],
        ];
    }

    /** @param array<string,mixed> $delivery @return array<string,mixed> */
    private function deliveryDocument(array $delivery, string $lang): array
    {
        $amountHt = 0.0;
        $items = array_map(static function (array $item) use (&$amountHt): array {
            $lineTotal = 0.0;
            return [
                'productCode' => $item['productCode'],
                'productName' => $item['productName'],
                'unit' => $item['unit'],
                'quantity' => (float) $item['quantity'],
                'unitPrice' => $lineTotal,
                'discountPct' => 0.0,
                'vatRate' => 0.0,
                'lineTotal' => $lineTotal,
                'batchNumber' => $item['batchNumber'],
            ];
        }, $delivery['items']);

        return [
            'type' => 'DELIVERY',
            'lang' => $lang,
            'number' => $delivery['noteNumber'],
            'date' => substr((string) $delivery['preparedAt'], 0, 10),
            'secondaryDateLabel' => 'deliveredAt',
            'secondaryDate' => $delivery['deliveredAt'],
            'status' => $delivery['status'],
            'customer' => [
                'code' => $delivery['customerCode'],
                'name' => $delivery['customerName'],
                'address' => $delivery['customerAddress'],
                'wilaya' => $delivery['customerWilaya'],
                'phone' => $delivery['customerPhone'],
            ],
            'items' => $items,
            'amountHt' => $amountHt,
            'vatAmount' => 0.0,
            'totalTtc' => $amountHt,
            'paidAmount' => 0.0,
            'notes' => (string) $delivery['notes'],
            'reference' => $delivery['orderNumber'],
            'extra' => ['signedBy' => $delivery['signedBy'], 'route' => $delivery['routeNumber']],
        ];
    }

    /** @param array<string,mixed> $invoice @return array<string,mixed> */
    private function invoiceDocument(array $invoice, string $lang): array
    {
        return [
            'type' => 'INVOICE',
            'lang' => $lang,
            'number' => $invoice['invoiceNumber'],
            'date' => $invoice['invoiceDate'],
            'secondaryDateLabel' => 'dueDate',
            'secondaryDate' => $invoice['dueDate'],
            'status' => $invoice['status'],
            'customer' => [
                'code' => $invoice['customerCode'],
                'name' => $invoice['customerName'],
                'address' => $invoice['customerAddress'],
                'wilaya' => $invoice['customerWilaya'],
                'phone' => $invoice['customerPhone'],
            ],
            'items' => array_map(static fn (array $item): array => [
                'productCode' => $item['productCode'],
                'productName' => $item['productNameAr'] !== null && $lang === 'ar' && $item['productNameAr'] !== ''
                    ? $item['productNameAr'] : $item['productName'],
                'unit' => $item['unit'],
                'quantity' => (float) $item['quantity'],
                'unitPrice' => (float) $item['unitPrice'],
                'discountPct' => (float) $item['discountPct'],
                'vatRate' => (float) $item['vatRate'],
                'lineTotal' => (float) $item['lineTotal'],
                'batchNumber' => $item['batchNumber'],
            ], $invoice['items']),
            'amountHt' => (float) $invoice['amountHt'],
            'vatAmount' => (float) $invoice['vatAmount'],
            'totalTtc' => (float) $invoice['totalTtc'],
            'paidAmount' => (float) $invoice['paidAmount'],
            'notes' => (string) $invoice['notes'],
            'reference' => $invoice['orderNumber'],
            'extra' => ['finalizedAt' => $invoice['finalizedAt']],
        ];
    }

    /** @param array<string,mixed> $statement @return array<string,mixed> */
    private function statementDocument(array $statement, string $lang): array
    {
        return [
            'type' => 'STATEMENT',
            'lang' => $lang,
            'number' => (string) $statement['customer']['code'],
            'date' => date('Y-m-d'),
            'secondaryDateLabel' => null,
            'secondaryDate' => null,
            'status' => 'OPEN',
            'customer' => [
                'code' => $statement['customer']['code'],
                'name' => $statement['customer']['name'],
                'address' => '',
                'wilaya' => (string) $statement['customer']['wilaya'],
                'phone' => (string) $statement['customer']['phone'],
            ],
            'items' => [],
            'lines' => $statement['lines'],
            'amountHt' => (float) $statement['totalDebit'],
            'vatAmount' => 0.0,
            'totalTtc' => (float) $statement['closingBalance'],
            'paidAmount' => (float) $statement['totalCredit'],
            'notes' => '',
            'reference' => null,
            'extra' => ['creditLimit' => $statement['customer']['creditLimit'],
                'balance' => $statement['customer']['balance']],
        ];
    }

    // -------------------------------------------------------------------- helpers

    /** @return array<string,mixed> */
    private function customer(int $id): array
    {
        if ($id <= 0) {
            throw ApiException::validation(['customerId' => 'validation.required']);
        }
        $row = $this->db->first('SELECT * FROM customers WHERE id = :id AND deletedAt IS NULL', ['id' => $id]);
        if ($row === null) {
            throw ApiException::notFound('Customer');
        }

        return $row;
    }

    /**
     * Normalises the submitted lines and prices them: an explicit unit price wins, otherwise the price
     * of the customer's segment applies.
     *
     * @param array<string,mixed> $data
     * @return array<int,array{productId:int,quantity:float,unitPrice:float,discountPct:float,vatRate:float,lineTotal:float,lineVat:float,productBatchId:?int}>
     */
    private function lines(array $data, string $segment): array
    {
        $payload = $data['items'] ?? null;
        if (!is_array($payload) || $payload === []) {
            throw ApiException::validation(['items' => 'validation.required']);
        }

        $lines = [];
        foreach ($payload as $raw) {
            $productId = (int) ($raw['productId'] ?? 0);
            $quantity = round((float) ($raw['quantity'] ?? 0), 3);
            if ($quantity <= 0) {
                throw ApiException::validation(['items' => 'validation.positive']);
            }
            $product = $this->db->first(
                'SELECT id, sku, retailPrice, wholesalePrice, specialPrice, vatRate
                 FROM products WHERE id = :id AND deletedAt IS NULL',
                ['id' => $productId]
            );
            if ($product === null) {
                throw ApiException::validation(['items' => 'validation.exists']);
            }
            $unitPrice = array_key_exists('unitPrice', $raw) && (float) $raw['unitPrice'] > 0
                ? round((float) $raw['unitPrice'], 2)
                : $this->priceFor($product, $segment);
            $discountPct = round((float) ($raw['discountPct'] ?? 0), 4);
            if ($discountPct < 0 || $discountPct > 100) {
                throw ApiException::validation(['items' => 'validation.range']);
            }
            $vatRate = array_key_exists('vatRate', $raw) && $raw['vatRate'] !== null
                ? round((float) $raw['vatRate'], 4) : (float) $product['vatRate'];

            $lines[] = $this->line(
                (int) $product['id'],
                $quantity,
                $unitPrice,
                $discountPct,
                $vatRate,
                isset($raw['productBatchId']) ? (int) $raw['productBatchId'] : null
            );
        }

        return $lines;
    }

    /**
     * One priced line: the net amount and the VAT are always derived here, so a line built from an
     * order and a line built from a payload cannot disagree on their arithmetic.
     *
     * @return array{productId:int,quantity:float,unitPrice:float,discountPct:float,vatRate:float,lineTotal:float,lineVat:float,productBatchId:?int}
     */
    private function line(int $productId, float $quantity, float $unitPrice, float $discountPct, float $vatRate,
                          ?int $productBatchId): array
    {
        $lineTotal = round($quantity * $unitPrice * (1 - $discountPct / 100), 2);

        return [
            'productId' => $productId,
            'quantity' => $quantity,
            'unitPrice' => $unitPrice,
            'discountPct' => $discountPct,
            'vatRate' => $vatRate,
            'lineTotal' => $lineTotal,
            'lineVat' => round($lineTotal * $vatRate / 100, 2),
            'productBatchId' => $productBatchId,
        ];
    }

    /** @param array<string,mixed> $product */
    private function priceFor(array $product, string $segment): float
    {
        $price = match (strtoupper($segment)) {
            'WHOLESALE' => (float) $product['wholesalePrice'],
            'SPECIAL' => (float) $product['specialPrice'],
            default => (float) $product['retailPrice'],
        };
        if ($price <= 0) {
            $price = (float) $product['retailPrice'] > 0 ? (float) $product['retailPrice']
                : (float) $product['wholesalePrice'];
        }

        return round($price, 2);
    }

    /** @param array<int,array> $lines @return array{0:float,1:float} */
    private function totals(array $lines): array
    {
        $amountHt = 0.0;
        $vat = 0.0;
        foreach ($lines as $line) {
            $amountHt += (float) $line['lineTotal'];
            $vat += (float) $line['lineVat'];
        }

        return [round($amountHt, 2), round($vat, 2)];
    }

    /** @param array<int,array> $lines */
    private function insertQuotationItems(int $quotationId, array $lines): void
    {
        foreach ($lines as $line) {
            $this->db->execute(
                'INSERT INTO quotationItems (quotationId, productId, quantity, unitPrice, discountPct, vatRate)
                 VALUES (:quotationId, :productId, :quantity, :unitPrice, :discountPct, :vatRate)',
                [
                    'quotationId' => $quotationId,
                    'productId' => $line['productId'],
                    'quantity' => $line['quantity'],
                    'unitPrice' => $line['unitPrice'],
                    'discountPct' => $line['discountPct'],
                    'vatRate' => $line['vatRate'],
                ]
            );
        }
    }

    /** @param array<int,array> $lines */
    private function insertInvoiceItems(int $invoiceId, array $lines): void
    {
        foreach ($lines as $line) {
            $this->db->execute(
                'INSERT INTO invoiceItems (invoiceId, productId, productBatchId, quantity, unitPrice, discountPct,
                        vatRate, lineTotal)
                 VALUES (:invoiceId, :productId, :productBatchId, :quantity, :unitPrice, :discountPct, :vatRate,
                        :lineTotal)',
                [
                    'invoiceId' => $invoiceId,
                    'productId' => $line['productId'],
                    'productBatchId' => $line['productBatchId'] ?? null,
                    'quantity' => $line['quantity'],
                    'unitPrice' => $line['unitPrice'],
                    'discountPct' => $line['discountPct'],
                    'vatRate' => $line['vatRate'],
                    'lineTotal' => $line['lineTotal'],
                ]
            );
        }
    }

    /**
     * FEFO: earliest expiry first, only RELEASED batches, across warehouses. The rows are locked so
     * two deliveries cannot promise the same bottle.
     *
     * @return array<int,array{batchId:int,warehouseId:int,quantity:float,unitCost:float,batchNumber:string}>
     */
    private function allocate(int $productId, float $quantity): array
    {
        $stock = $this->db->select(
            'SELECT b.id AS batchId, b.warehouseId, b.quantity, b.unitCost, b.batchNumber
             FROM productBatches b
             WHERE b.productId = :productId AND b.qualityStatus = \'RELEASED\' AND b.quantity > 0
             ORDER BY b.expiryDate IS NULL, b.expiryDate ASC, b.batchNumber ASC
             FOR UPDATE',
            ['productId' => $productId]
        );

        $available = 0.0;
        foreach ($stock as $batch) {
            $available += (float) $batch['quantity'];
        }

        $allocations = [];
        $remaining = round($quantity, 3);
        foreach ($stock as $batch) {
            if ($remaining <= 0.0001) {
                break;
            }
            $take = min($remaining, round((float) $batch['quantity'], 3));
            if ($take <= 0) {
                continue;
            }
            $allocations[] = [
                'batchId' => (int) $batch['batchId'],
                'warehouseId' => (int) $batch['warehouseId'],
                'quantity' => $take,
                'unitCost' => (float) $batch['unitCost'],
                'batchNumber' => (string) $batch['batchNumber'],
            ];
            $remaining = round($remaining - $take, 3);
        }

        if ($remaining > 0.0001) {
            $product = $this->db->first('SELECT sku, nameFr FROM products WHERE id = :id', ['id' => $productId]);
            throw ApiException::conflict(
                'There is not enough released stock to ship this line.',
                'SALES_STOCK_INSUFFICIENT',
                [
                    'productId' => $productId,
                    'productCode' => $product['sku'] ?? null,
                    'productName' => $product['nameFr'] ?? null,
                    'requested' => $quantity,
                    'available' => round($available, 3),
                    'missing' => $remaining,
                ]
            );
        }

        return $allocations;
    }

    /** @param array{batchId:int,warehouseId:int,quantity:float,unitCost:float} $allocation */
    private function moveOut(array $allocation, int $noteId, string $noteNumber, int $userId, string $reason): void
    {
        $this->db->execute(
            'UPDATE productBatches SET quantity = quantity - :quantity WHERE id = :batchId',
            ['quantity' => $allocation['quantity'], 'batchId' => $allocation['batchId']]
        );
        $this->db->execute(
            'INSERT INTO stockMovements (movementNumber, movementType, warehouseId, productBatchId, quantity,
                    unitCost, referenceType, referenceId, reason, userId, occurredAt)
             VALUES (:number, \'OUT\', :warehouseId, :batchId, :quantity, :unitCost, \'DELIVERY_NOTE\', :noteId,
                    :reason, :userId, NOW())',
            [
                'number' => $this->nextNumber('stockMovements', 'movementNumber', 'MVT'),
                'warehouseId' => $allocation['warehouseId'],
                'batchId' => $allocation['batchId'],
                'quantity' => $allocation['quantity'],
                'unitCost' => $allocation['unitCost'],
                'noteId' => $noteId,
                'reason' => mb_substr($reason, 0, 255),
                'userId' => $userId,
            ]
        );
    }

    /** The batch an invoice line points at, so the traceability chain survives billing. */
    private function deliveredBatch(int $orderId, int $productId): ?int
    {
        $row = $this->db->first(
            'SELECT di.productBatchId
             FROM deliveryItems di JOIN deliveryNotes d ON d.id = di.deliveryNoteId
             WHERE d.orderId = :orderId AND di.productId = :productId AND d.status <> \'RETURNED\'
             ORDER BY di.id LIMIT 1',
            ['orderId' => $orderId, 'productId' => $productId]
        );

        return $row === null ? null : (int) $row['productBatchId'];
    }

    /**
     * Recomputes the order status from its lines: INVOICED when every unit is billed, PARTIAL once
     * something left the warehouse, RESERVED while a note is only prepared, CONFIRMED otherwise.
     */
    private function syncOrderAfterDelivery(int $orderId, int $userId): void
    {
        $lines = $this->db->select(
            'SELECT quantity, deliveredQty, invoicedQty FROM salesOrderItems WHERE orderId = :id', ['id' => $orderId]);
        $allDelivered = $lines !== [];
        $allInvoiced = $lines !== [];
        $anyMoved = false;
        foreach ($lines as $line) {
            $quantity = (float) $line['quantity'];
            $delivered = (float) $line['deliveredQty'];
            $invoiced = (float) $line['invoicedQty'];
            if ($delivered + 0.0001 < $quantity) {
                $allDelivered = false;
            }
            if ($invoiced + 0.0001 < $quantity) {
                $allInvoiced = false;
            }
            if ($delivered > 0 || $invoiced > 0) {
                $anyMoved = true;
            }
        }

        if ($allInvoiced) {
            $status = 'INVOICED';
        } elseif ($anyMoved) {
            $status = 'PARTIAL';
        } elseif ($allDelivered) {
            $status = 'RESERVED';
        } else {
            $open = $this->db->first(
                'SELECT id FROM deliveryNotes WHERE orderId = :id AND status IN (\'PREPARED\', \'LOADED\', \'IN_TRANSIT\')
                 LIMIT 1',
                ['id' => $orderId]
            );
            $status = $open === null ? 'CONFIRMED' : 'RESERVED';
        }

        $this->db->execute('UPDATE salesOrders SET status = :status, updatedBy = :userId WHERE id = :id',
            ['status' => $status, 'userId' => $userId, 'id' => $orderId]);
    }

    /**
     * Refuses the operation that would push the customer past their limit. A limit of 0 means the
     * customer buys cash and carries no credit at all — the balance must stay at or below zero.
     */
    private function creditGuard(int $customerId, float $amount, string $context): void
    {
        $customer = $this->db->first('SELECT name, creditLimit, balance FROM customers WHERE id = :id',
            ['id' => $customerId]);
        if ($customer === null) {
            throw ApiException::notFound('Customer');
        }
        $limit = (float) $customer['creditLimit'];
        $balance = (float) $customer['balance'];
        if ($limit <= 0) {
            return;
        }
        if (round($balance + $amount, 2) > $limit + 0.001) {
            throw ApiException::conflict(
                'The customer has reached their credit limit: collect the outstanding balance first.',
                'CREDIT_LIMIT_EXCEEDED',
                [
                    'context' => $context,
                    'customer' => (string) $customer['name'],
                    'creditLimit' => $limit,
                    'balance' => $balance,
                    'requested' => $amount,
                    'available' => round($limit - $balance, 2),
                ]
            );
        }
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateQuotation(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'quoteNumber' => (string) $row['quoteNumber'],
            'customerId' => (int) $row['customerId'],
            'customerName' => (string) ($row['customerName'] ?? ''),
            'customerCode' => (string) ($row['customerCode'] ?? ''),
            'priceSegment' => (string) ($row['priceSegment'] ?? ''),
            'status' => (string) $row['status'],
            'quoteDate' => (string) $row['quoteDate'],
            'validUntil' => $row['validUntil'],
            'amountHt' => round((float) $row['amountHt'], 2),
            'vatAmount' => round((float) $row['vatAmount'], 2),
            'totalTtc' => round((float) $row['totalTtc'], 2),
            'notes' => (string) $row['notes'],
            'representativeName' => $row['representativeName'] ?? null,
            'lineCount' => (int) ($row['lineCount'] ?? 0),
            'orderCount' => (int) ($row['orderCount'] ?? 0),
            'customerPhone' => $row['customerPhone'] ?? null,
            'customerAddress' => $row['customerAddress'] ?? null,
            'customerWilaya' => $row['customerWilaya'] ?? null,
            'createdAt' => $row['createdAt'] ?? null,
        ];
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateOrder(array $row): array
    {
        $ordered = (float) ($row['orderedQty'] ?? 0);
        $delivered = (float) ($row['deliveredQty'] ?? 0);
        $invoiced = (float) ($row['invoicedQty'] ?? 0);

        return [
            'id' => (int) $row['id'],
            'orderNumber' => (string) $row['orderNumber'],
            'customerId' => (int) $row['customerId'],
            'customerName' => (string) ($row['customerName'] ?? ''),
            'customerCode' => (string) ($row['customerCode'] ?? ''),
            'priceSegment' => (string) ($row['priceSegment'] ?? ''),
            'customerBalance' => round((float) ($row['balance'] ?? 0), 2),
            'creditLimit' => round((float) ($row['creditLimit'] ?? 0), 2),
            'quotationId' => $row['quotationId'] === null ? null : (int) $row['quotationId'],
            'quoteNumber' => $row['quoteNumber'] ?? null,
            'status' => (string) $row['status'],
            'orderDate' => (string) $row['orderDate'],
            'deliveryDate' => $row['deliveryDate'],
            'amountHt' => round((float) $row['amountHt'], 2),
            'vatAmount' => round((float) $row['vatAmount'], 2),
            'totalTtc' => round((float) $row['totalTtc'], 2),
            'notes' => (string) $row['notes'],
            'representativeName' => $row['representativeName'] ?? null,
            'lineCount' => (int) ($row['lineCount'] ?? 0),
            'deliveryCount' => (int) ($row['deliveryCount'] ?? 0),
            'orderedQty' => round($ordered, 3),
            'deliveredQty' => round($delivered, 3),
            'invoicedQty' => round($invoiced, 3),
            'deliveryProgress' => $ordered > 0 ? round($delivered / $ordered * 100, 1) : 0.0,
            'invoiceProgress' => $ordered > 0 ? round($invoiced / $ordered * 100, 1) : 0.0,
            'customerPhone' => $row['customerPhone'] ?? null,
            'customerAddress' => $row['customerAddress'] ?? null,
            'customerWilaya' => $row['customerWilaya'] ?? null,
            'createdAt' => $row['createdAt'] ?? null,
        ];
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateDelivery(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'noteNumber' => (string) $row['noteNumber'],
            'orderId' => (int) $row['orderId'],
            'orderNumber' => (string) ($row['orderNumber'] ?? ''),
            'customerId' => (int) $row['customerId'],
            'customerName' => (string) ($row['customerName'] ?? ''),
            'customerCode' => (string) ($row['customerCode'] ?? ''),
            'routeId' => $row['routeId'] === null ? null : (int) $row['routeId'],
            'routeNumber' => $row['routeNumber'] ?? null,
            'status' => (string) $row['status'],
            'preparedAt' => (string) $row['preparedAt'],
            'deliveredAt' => $row['deliveredAt'],
            'signedBy' => (string) $row['signedBy'],
            'signaturePath' => $row['signaturePath'],
            'notes' => (string) $row['notes'],
            'lineCount' => (int) ($row['lineCount'] ?? 0),
            'totalQuantity' => round((float) ($row['totalQuantity'] ?? 0), 3),
            'orderTotal' => round((float) ($row['orderTotal'] ?? 0), 2),
            'customerPhone' => $row['customerPhone'] ?? null,
            'customerAddress' => $row['customerAddress'] ?? null,
            'customerWilaya' => $row['customerWilaya'] ?? null,
        ];
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateInvoice(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'invoiceNumber' => (string) $row['invoiceNumber'],
            'customerId' => (int) $row['customerId'],
            'customerName' => (string) ($row['customerName'] ?? ''),
            'customerCode' => (string) ($row['customerCode'] ?? ''),
            'orderId' => $row['orderId'] === null ? null : (int) $row['orderId'],
            'orderNumber' => $row['orderNumber'] ?? null,
            'invoiceDate' => (string) $row['invoiceDate'],
            'dueDate' => $row['dueDate'],
            'amountHt' => round((float) $row['amountHt'], 2),
            'vatAmount' => round((float) $row['vatAmount'], 2),
            'totalTtc' => round((float) $row['totalTtc'], 2),
            'paidAmount' => round((float) $row['paidAmount'], 2),
            'remaining' => round((float) $row['totalTtc'] - (float) $row['paidAmount'], 2),
            'status' => (string) $row['status'],
            'currency' => (string) $row['currency'],
            'finalizedAt' => $row['finalizedAt'],
            'isFinalized' => $row['finalizedAt'] !== null,
            'printedCount' => (int) $row['printedCount'],
            'isOverdue' => (bool) ($row['isOverdue'] ?? false),
            'notes' => (string) $row['notes'],
            'lineCount' => (int) ($row['lineCount'] ?? 0),
            'customerPhone' => $row['customerPhone'] ?? null,
            'customerAddress' => $row['customerAddress'] ?? null,
            'customerWilaya' => $row['customerWilaya'] ?? null,
            'customerEmail' => $row['customerEmail'] ?? null,
            'createdAt' => $row['createdAt'] ?? null,
        ];
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydratePayment(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'paymentNumber' => (string) $row['paymentNumber'],
            'customerId' => (int) $row['customerId'],
            'customerName' => (string) ($row['customerName'] ?? ''),
            'customerCode' => (string) ($row['customerCode'] ?? ''),
            'invoiceId' => $row['invoiceId'] === null ? null : (int) $row['invoiceId'],
            'invoiceNumber' => $row['invoiceNumber'] ?? null,
            'invoiceTotal' => round((float) ($row['invoiceTotal'] ?? 0), 2),
            'cashAccountId' => (int) $row['cashAccountId'],
            'cashAccountName' => (string) ($row['cashAccountName'] ?? ''),
            'amount' => round((float) $row['amount'], 2),
            'paidAt' => (string) $row['paidAt'],
            'method' => (string) $row['method'],
            'reference' => (string) $row['reference'],
            'userName' => $row['userName'] ?? null,
        ];
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateCreditNote(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'creditNumber' => (string) $row['creditNumber'],
            'invoiceId' => (int) $row['invoiceId'],
            'invoiceNumber' => (string) ($row['invoiceNumber'] ?? ''),
            'invoiceTotal' => round((float) ($row['invoiceTotal'] ?? 0), 2),
            'customerId' => (int) $row['customerId'],
            'customerName' => (string) ($row['customerName'] ?? ''),
            'creditDate' => (string) $row['creditDate'],
            'reason' => (string) $row['reason'],
            'amountHt' => round((float) $row['amountHt'], 2),
            'vatAmount' => round((float) $row['vatAmount'], 2),
            'totalTtc' => round((float) $row['totalTtc'], 2),
            'status' => (string) $row['status'],
        ];
    }

    private function nextNumber(string $table, string $column, string $prefix): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM ' . $table) + 1;
        do {
            $number = sprintf('%s-%05d', $prefix, $sequence);
            $exists = $this->db->first('SELECT id FROM ' . $table . ' WHERE ' . $column . ' = :number',
                ['number' => $number]) !== null;
            $sequence++;
        } while ($exists);

        return $number;
    }

    private function date(mixed $value): ?string
    {
        if (!is_string($value) || trim($value) === '') {
            return null;
        }
        $parsed = \DateTimeImmutable::createFromFormat('Y-m-d', trim($value));

        return $parsed === false ? null : $parsed->format('Y-m-d');
    }

    private function dateTime(mixed $value): ?string
    {
        if (!is_string($value) || trim($value) === '') {
            return null;
        }
        $parsed = \DateTimeImmutable::createFromFormat('Y-m-d H:i:s', trim($value));

        return $parsed === false ? null : $parsed->format('Y-m-d H:i:s');
    }

    private function path(mixed $value): ?string
    {
        $path = trim((string) ($value ?? ''));

        return $path === '' ? null : mb_substr($path, 0, 255);
    }

    /** @param array<int,string> $allowed */
    private function wrongStatus(string $what, string $current, array $allowed): ApiException
    {
        return ApiException::conflict(
            $what . ' is ' . $current . '; this action needs ' . implode(' or ', $allowed) . '.',
            'INVALID_STATUS',
            ['current' => $current, 'allowed' => $allowed]
        );
    }
}
