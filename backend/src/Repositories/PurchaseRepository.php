<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Purchase orders and goods receptions.
 *
 * <p>Purchasing is the only door through which stock enters the warehouse:</p>
 * <ul>
 *   <li>an order is created as a DRAFT, sent to the supplier, and only then can be received;</li>
 *   <li>a reception never receives more than what remains on the line, and it creates the batch
 *       (with its number and expiry) plus the IN movement in the same transaction;</li>
 *   <li>received goods start in QUARANTINE unless a qualified user releases them, and released
 *       goods can never be stored in the quarantine warehouse;</li>
 *   <li>an order that has received something can no longer be cancelled — the stock is real.</li>
 * </ul>
 */
final class PurchaseRepository
{
    private const STATUSES = ['DRAFT', 'SENT', 'PARTIAL', 'RECEIVED', 'CANCELLED'];

    public function __construct(private readonly Database $db, private readonly InventoryRepository $inventory)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, ?string $status, ?int $supplierId,
                             ?string $from, ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];

        if ($search !== '') {
            $where[] = '(po.orderNumber LIKE :searchNumber OR s.name LIKE :searchSupplier OR s.code LIKE :searchCode)';
            $params['searchNumber'] = '%' . $search . '%';
            $params['searchSupplier'] = '%' . $search . '%';
            $params['searchCode'] = '%' . $search . '%';
        }
        if ($status !== null && $status !== '' && in_array(strtoupper($status), self::STATUSES, true)) {
            $where[] = 'po.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($supplierId !== null && $supplierId > 0) {
            $where[] = 'po.supplierId = :supplierId';
            $params['supplierId'] = $supplierId;
        }
        if ($from !== null && $from !== '') {
            $where[] = 'po.orderDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'po.orderDate <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM purchaseOrders po JOIN suppliers s ON s.id = po.supplierId ' . $clause,
            $params
        );

        $rows = $this->db->select(
            'SELECT po.id, po.orderNumber, po.supplierId, s.name AS supplierName, s.code AS supplierCode,
                    po.status, po.orderDate, po.expectedDate, po.amountHt, po.vatAmount, po.totalTtc,
                    po.currency, po.notes, po.createdAt, po.updatedAt,
                    (SELECT COUNT(*) FROM purchaseOrderItems i WHERE i.orderId = po.id) AS itemCount,
                    (SELECT COALESCE(SUM(i.receivedQty), 0) FROM purchaseOrderItems i WHERE i.orderId = po.id)
                        AS receivedQuantity,
                    (SELECT COUNT(*) FROM goodsReceipts r WHERE r.orderId = po.id) AS receiptCount
             FROM purchaseOrders po
             JOIN suppliers s ON s.id = po.supplierId
             ' . $clause . '
             ORDER BY po.orderDate DESC, po.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateOrder'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function find(int $id): array
    {
        $order = $this->order($id);
        $items = $this->db->select(
            'SELECT i.id, i.materialId, m.code AS materialCode, m.commercialName AS materialName,
                    i.quantity, i.unit, i.unitPrice, i.vatRate, i.receivedQty,
                    ROUND(i.quantity - i.receivedQty, 3) AS remainingQty,
                    ROUND(i.quantity * i.unitPrice, 2) AS lineTotal
             FROM purchaseOrderItems i
             JOIN rawMaterials m ON m.id = i.materialId
             WHERE i.orderId = :id ORDER BY i.id',
            ['id' => $id]
        );
        $order['items'] = array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'materialId' => (int) $row['materialId'],
            'materialCode' => (string) $row['materialCode'],
            'materialName' => (string) $row['materialName'],
            'quantity' => round((float) $row['quantity'], 3),
            'unit' => (string) $row['unit'],
            'unitPrice' => round((float) $row['unitPrice'], 2),
            'vatRate' => round((float) $row['vatRate'], 4),
            'receivedQty' => round((float) $row['receivedQty'], 3),
            'remainingQty' => round((float) $row['remainingQty'], 3),
            'lineTotal' => round((float) $row['lineTotal'], 2),
        ], $items);

        $receipts = $this->db->select(
            'SELECT r.id, r.receiptNumber, r.warehouseId, w.name AS warehouseName, r.receivedAt,
                    r.receivedBy, u.username AS receivedByName, r.notes,
                    (SELECT COUNT(*) FROM goodsReceiptItems gi WHERE gi.receiptId = r.id) AS itemCount
             FROM goodsReceipts r
             JOIN warehouses w ON w.id = r.warehouseId
             LEFT JOIN users u ON u.id = r.receivedBy
             WHERE r.orderId = :id ORDER BY r.receivedAt DESC, r.id DESC',
            ['id' => $id]
        );
        $order['receipts'] = array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'receiptNumber' => (string) $row['receiptNumber'],
            'warehouseId' => (int) $row['warehouseId'],
            'warehouseName' => (string) $row['warehouseName'],
            'receivedAt' => (string) $row['receivedAt'],
            'receivedByName' => (string) ($row['receivedByName'] ?? ''),
            'notes' => (string) $row['notes'],
            'itemCount' => (int) $row['itemCount'],
        ], $receipts);

        return $order;
    }

    /**
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function create(array $data, int $userId): array
    {
        $supplierId = (int) ($data['supplierId'] ?? 0);
        $lines = is_array($data['items'] ?? null) ? $data['items'] : [];
        $errors = [];
        if ($supplierId <= 0) {
            $errors['supplierId'] = 'validation.required';
        }
        if ($lines === []) {
            $errors['items'] = 'validation.required';
        }
        if ($errors !== []) {
            throw ApiException::validation($errors);
        }

        $supplier = $this->db->first('SELECT id, name, isActive FROM suppliers
                                       WHERE id = :id AND deletedAt IS NULL', ['id' => $supplierId]);
        if ($supplier === null) {
            throw ApiException::notFound('Supplier');
        }
        if ((int) $supplier['isActive'] !== 1) {
            throw ApiException::conflict('The supplier is inactive: a new order cannot be placed.',
                'SUPPLIER_INACTIVE');
        }

        $orderDate = $this->date($data['orderDate'] ?? null) ?? (new \DateTimeImmutable('today'))->format('Y-m-d');
        $expectedDate = $this->date($data['expectedDate'] ?? null);
        if ($expectedDate !== null && $expectedDate < $orderDate) {
            throw ApiException::validation(['expectedDate' => 'validation.range']);
        }

        $prepared = [];
        $amountHt = 0.0;
        $vatAmount = 0.0;
        foreach (array_values($lines) as $index => $line) {
            $materialId = (int) ($line['materialId'] ?? 0);
            $quantity = round((float) ($line['quantity'] ?? 0), 3);
            $unitPrice = round((float) ($line['unitPrice'] ?? 0), 2);
            $vatRate = round((float) ($line['vatRate'] ?? 19), 4);
            if ($materialId <= 0 || $quantity <= 0 || $unitPrice < 0 || $vatRate < 0 || $vatRate > 100) {
                throw ApiException::validation(['items.' . $index => 'validation.range']);
            }
            $material = $this->db->first('SELECT id, storageUnit FROM rawMaterials
                                          WHERE id = :id AND deletedAt IS NULL', ['id' => $materialId]);
            if ($material === null) {
                throw ApiException::validation(['items.' . $index . '.materialId' => 'validation.exists']);
            }
            $unit = trim((string) ($line['unit'] ?? '')) ?: (string) $material['storageUnit'];
            $lineHt = round($quantity * $unitPrice, 2);
            $lineVat = round($lineHt * $vatRate / 100, 2);
            $amountHt += $lineHt;
            $vatAmount += $lineVat;
            $prepared[] = [
                'materialId' => $materialId,
                'quantity' => $quantity,
                'unit' => $unit,
                'unitPrice' => $unitPrice,
                'vatRate' => $vatRate,
            ];
        }

        $orderId = $this->db->transaction(function () use ($prepared, $supplierId, $orderDate, $expectedDate,
            $data, $userId, $amountHt, $vatAmount): int {
            $this->db->execute(
                'INSERT INTO purchaseOrders (orderNumber, supplierId, requestId, status, orderDate, expectedDate,
                        amountHt, vatAmount, totalTtc, currency, notes, createdBy, updatedBy)
                 VALUES (:orderNumber, :supplierId, :requestId, \'DRAFT\', :orderDate, :expectedDate,
                        :amountHt, :vatAmount, :totalTtc, \'DZD\', :notes, :createdBy, :updatedBy)',
                [
                    'orderNumber' => $this->nextNumber('purchaseOrders', 'orderNumber', 'BC'),
                    'supplierId' => $supplierId,
                    'requestId' => ((int) ($data['requestId'] ?? 0)) > 0 ? (int) $data['requestId'] : null,
                    'orderDate' => $orderDate,
                    'expectedDate' => $expectedDate,
                    'amountHt' => round($amountHt, 2),
                    'vatAmount' => round($vatAmount, 2),
                    'totalTtc' => round($amountHt + $vatAmount, 2),
                    'notes' => trim((string) ($data['notes'] ?? '')),
                    'createdBy' => $userId,
                    'updatedBy' => $userId,
                ]
            );
            $orderId = $this->db->lastId();
            foreach ($prepared as $line) {
                $this->db->execute(
                    'INSERT INTO purchaseOrderItems (orderId, materialId, quantity, unit, unitPrice, vatRate, receivedQty)
                     VALUES (:orderId, :materialId, :quantity, :unit, :unitPrice, :vatRate, 0)',
                    $line + ['orderId' => $orderId]
                );
            }

            return $orderId;
        });

        return $this->find($orderId);
    }

    /** @return array<string,mixed> */
    public function send(int $id, int $userId): array
    {
        $order = $this->order($id);
        if ((string) $order['status'] !== 'DRAFT') {
            throw ApiException::conflict('Only a draft order can be sent to the supplier.', 'PO_INVALID_STATE');
        }
        $this->db->execute('UPDATE purchaseOrders SET status = \'SENT\', updatedBy = :updatedBy WHERE id = :id',
            ['updatedBy' => $userId, 'id' => $id]);

        return $this->find($id);
    }

    /** @return array<string,mixed> */
    public function cancel(int $id, int $userId): array
    {
        $order = $this->order($id);
        $status = (string) $order['status'];
        if ($status === 'CANCELLED') {
            throw ApiException::conflict('This order is already cancelled.', 'PO_INVALID_STATE');
        }
        if ($status === 'RECEIVED') {
            throw ApiException::conflict('A fully received order cannot be cancelled.', 'PO_INVALID_STATE');
        }
        $received = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(receivedQty), 0) FROM purchaseOrderItems WHERE orderId = :id', ['id' => $id]
        );
        if ($received > 0.0001) {
            throw ApiException::conflict('Part of this order is already in stock: it cannot be cancelled.',
                'PO_ALREADY_RECEIVED');
        }
        $this->db->execute('UPDATE purchaseOrders SET status = \'CANCELLED\', updatedBy = :updatedBy WHERE id = :id',
            ['updatedBy' => $userId, 'id' => $id]);

        return $this->find($id);
    }

    /**
     * Receives goods against an order: creates the receipt, the batches and the IN movements,
     * then recomputes the order status from what remains to be received.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function receive(int $orderId, array $data, int $userId): array
    {
        $order = $this->order($orderId);
        $status = (string) $order['status'];
        if (!in_array($status, ['SENT', 'PARTIAL'], true)) {
            throw ApiException::conflict('Only a sent order can be received (current status: ' . $status . ').',
                'PO_INVALID_STATE');
        }

        $warehouseId = (int) ($data['warehouseId'] ?? 0);
        $warehouse = $this->db->first('SELECT id, name, isQuarantine FROM warehouses
                                       WHERE id = :id AND deletedAt IS NULL AND isActive = 1',
            ['id' => $warehouseId]);
        if ($warehouse === null) {
            throw ApiException::notFound('Warehouse');
        }
        $release = (bool) ($data['release'] ?? false);
        if ($release && (int) $warehouse['isQuarantine'] === 1) {
            throw ApiException::conflict('Released goods cannot be received in the quarantine warehouse.',
                'QUARANTINE_RECEPTION');
        }

        $lines = is_array($data['items'] ?? null) ? $data['items'] : [];
        if ($lines === []) {
            throw ApiException::validation(['items' => 'validation.required']);
        }

        $itemsById = [];
        foreach ($this->db->select('SELECT id, materialId, quantity, unit, unitPrice, receivedQty
                                    FROM purchaseOrderItems WHERE orderId = :id', ['id' => $orderId]) as $row) {
            $itemsById[(int) $row['id']] = $row;
        }

        $prepared = [];
        foreach (array_values($lines) as $index => $line) {
            $orderItemId = (int) ($line['orderItemId'] ?? 0);
            if ($orderItemId <= 0) {
                foreach ($itemsById as $candidate) {
                    if ((int) $candidate['materialId'] === (int) ($line['materialId'] ?? 0)) {
                        $orderItemId = (int) $candidate['id'];
                        break;
                    }
                }
            }
            if ($orderItemId <= 0 || !isset($itemsById[$orderItemId])) {
                throw ApiException::validation(['items.' . $index . '.orderItemId' => 'validation.exists']);
            }
            $orderItem = $itemsById[$orderItemId];
            $remaining = round((float) $orderItem['quantity'] - (float) $orderItem['receivedQty'], 3);
            $quantity = round((float) ($line['quantity'] ?? 0), 3);
            if ($quantity <= 0) {
                throw ApiException::validation(['items.' . $index . '.quantity' => 'validation.range']);
            }
            $quantityError = $this->receiptQuantityError($quantity, $remaining, $index);
            if ($quantityError !== null) {
                throw $quantityError;
            }
            $batchNumber = trim((string) ($line['batchNumber'] ?? ''));
            if ($batchNumber === '') {
                throw ApiException::validation(['items.' . $index . '.batchNumber' => 'validation.required']);
            }
            $prepared[] = [
                'orderItemId' => $orderItemId,
                'materialId' => (int) $orderItem['materialId'],
                'quantity' => $quantity,
                'unit' => trim((string) ($line['unit'] ?? '')) ?: (string) $orderItem['unit'],
                'unitPrice' => round((float) ($line['unitPrice'] ?? $orderItem['unitPrice']), 2),
                'batchNumber' => $batchNumber,
                'expiryDate' => $this->date($line['expiryDate'] ?? null),
                'productionDate' => $this->date($line['productionDate'] ?? null),
            ];
        }

        $receiptId = $this->db->transaction(function () use ($orderId, $warehouseId, $userId, $data, $prepared,
            $release, $order): int {
            $this->db->execute(
                'INSERT INTO goodsReceipts (receiptNumber, orderId, warehouseId, receivedAt, receivedBy, notes)
                 VALUES (:receiptNumber, :orderId, :warehouseId, NOW(), :receivedBy, :notes)',
                [
                    'receiptNumber' => $this->nextNumber('goodsReceipts', 'receiptNumber', 'BR'),
                    'orderId' => $orderId,
                    'warehouseId' => $warehouseId,
                    'receivedBy' => $userId,
                    'notes' => trim((string) ($data['notes'] ?? '')),
                ]
            );
            $receiptId = $this->db->lastId();

            foreach ($prepared as $line) {
                $batchId = $this->batchForReception($line, $warehouseId, (int) $order['supplierId'], $release, $userId);
                $this->db->execute(
                    'INSERT INTO goodsReceiptItems (receiptId, materialId, quantity, unitPrice, batchNumber,
                            expiryDate, materialBatchId)
                     VALUES (:receiptId, :materialId, :quantity, :unitPrice, :batchNumber, :expiryDate, :materialBatchId)',
                    [
                        'receiptId' => $receiptId,
                        'materialId' => $line['materialId'],
                        'quantity' => $line['quantity'],
                        'unitPrice' => $line['unitPrice'],
                        'batchNumber' => $line['batchNumber'],
                        'expiryDate' => $line['expiryDate'],
                        'materialBatchId' => $batchId,
                    ]
                );
                $this->db->execute(
                    'UPDATE purchaseOrderItems SET receivedQty = receivedQty + :quantity WHERE id = :id',
                    ['quantity' => $line['quantity'], 'id' => $line['orderItemId']]
                );
                $this->inventory->postMovement([
                    'movementType' => 'IN',
                    'warehouseId' => $warehouseId,
                    'targetWarehouseId' => null,
                    'materialBatchId' => $batchId,
                    'productBatchId' => null,
                    'quantity' => $line['quantity'],
                    'unitCost' => $line['unitPrice'],
                    'referenceType' => 'GOODS_RECEIPT',
                    'referenceId' => $receiptId,
                    'reason' => 'Réception fournisseur',
                    'userId' => $userId,
                ]);
            }

            $open = (float) $this->db->scalar(
                'SELECT COALESCE(SUM(quantity - receivedQty), 0) FROM purchaseOrderItems WHERE orderId = :id',
                ['id' => $orderId]
            );
            $received = (float) $this->db->scalar(
                'SELECT COALESCE(SUM(receivedQty), 0) FROM purchaseOrderItems WHERE orderId = :id', ['id' => $orderId]
            );
            $newStatus = $open <= 0.0001 ? 'RECEIVED' : ($received > 0 ? 'PARTIAL' : 'SENT');
            $this->db->execute('UPDATE purchaseOrders SET status = :status, updatedBy = :updatedBy WHERE id = :id',
                ['status' => $newStatus, 'updatedBy' => $userId, 'id' => $orderId]);

            return $receiptId;
        });

        return $this->receipt($receiptId, $orderId);
    }

    // ------------------------------------------------------------------ internals

    private function receiptQuantityError(float $quantity, float $remaining, int $index): ?ApiException
    {
        if ($quantity > $remaining + 0.0001) {
            return ApiException::validation(['items.' . $index . '.quantity' => 'inventory.error.over_receipt']);
        }

        return null;
    }

    /** @param array<string,mixed> $line */
    private function batchForReception(array $line, int $warehouseId, int $supplierId, bool $release, int $userId): int
    {
        $quality = $release ? 'RELEASED' : 'QUARANTINE';
        $existing = $this->db->first(
            'SELECT id FROM materialBatches
             WHERE materialId = :materialId AND batchNumber = :batchNumber AND warehouseId = :warehouseId',
            ['materialId' => $line['materialId'], 'batchNumber' => $line['batchNumber'],
                'warehouseId' => $warehouseId]
        );
        if ($existing !== null) {
            $batchId = (int) $existing['id'];
            $this->db->execute(
                'UPDATE materialBatches SET quantity = quantity + :quantity,
                        expiryDate = COALESCE(:expiryDate, expiryDate),
                        productionDate = COALESCE(:productionDate, productionDate)
                 WHERE id = :id',
                [
                    'quantity' => $line['quantity'],
                    'expiryDate' => $line['expiryDate'],
                    'productionDate' => $line['productionDate'],
                    'id' => $batchId,
                ]
            );

            return $batchId;
        }

        $this->db->execute(
            'INSERT INTO materialBatches (materialId, batchNumber, warehouseId, supplierId, quantity, unit,
                    productionDate, expiryDate, qualityStatus, purchasePrice, releasedAt, releasedBy)
             VALUES (:materialId, :batchNumber, :warehouseId, :supplierId, :quantity, :unit,
                    :productionDate, :expiryDate, :qualityStatus, :purchasePrice, :releasedAt, :releasedBy)',
            [
                'materialId' => $line['materialId'],
                'batchNumber' => $line['batchNumber'],
                'warehouseId' => $warehouseId,
                'supplierId' => $supplierId > 0 ? $supplierId : null,
                'quantity' => $line['quantity'],
                'unit' => $line['unit'],
                'productionDate' => $line['productionDate'],
                'expiryDate' => $line['expiryDate'],
                'qualityStatus' => $quality,
                'purchasePrice' => $line['unitPrice'],
                'releasedAt' => $release ? (new \DateTimeImmutable())->format('Y-m-d H:i:s') : null,
                'releasedBy' => $release ? $userId : null,
            ]
        );

        return $this->db->lastId();
    }

    /** @return array<string,mixed> */
    private function receipt(int $receiptId, int $orderId): array
    {
        $header = $this->db->first(
            'SELECT r.id, r.receiptNumber, r.orderId, po.orderNumber, r.warehouseId, w.name AS warehouseName,
                    r.receivedAt, r.receivedBy, u.username AS receivedByName, r.notes
             FROM goodsReceipts r
             JOIN purchaseOrders po ON po.id = r.orderId
             JOIN warehouses w ON w.id = r.warehouseId
             LEFT JOIN users u ON u.id = r.receivedBy
             WHERE r.id = :id',
            ['id' => $receiptId]
        );
        if ($header === null) {
            throw ApiException::notFound('Goods receipt');
        }
        $items = $this->db->select(
            'SELECT gi.id, gi.materialId, m.code AS materialCode, m.commercialName AS materialName,
                    gi.quantity, gi.unitPrice, gi.batchNumber, gi.expiryDate, gi.materialBatchId,
                    b.qualityStatus, b.quantity AS batchQuantity
             FROM goodsReceiptItems gi
             JOIN rawMaterials m ON m.id = gi.materialId
             LEFT JOIN materialBatches b ON b.id = gi.materialBatchId
             WHERE gi.receiptId = :id ORDER BY gi.id',
            ['id' => $receiptId]
        );

        return [
            'id' => (int) $header['id'],
            'receiptNumber' => (string) $header['receiptNumber'],
            'orderId' => (int) $header['orderId'],
            'orderNumber' => (string) $header['orderNumber'],
            'warehouseId' => (int) $header['warehouseId'],
            'warehouseName' => (string) $header['warehouseName'],
            'receivedAt' => (string) $header['receivedAt'],
            'receivedByName' => (string) ($header['receivedByName'] ?? ''),
            'notes' => (string) $header['notes'],
            'orderStatus' => (string) $this->db->scalar('SELECT status FROM purchaseOrders WHERE id = :id',
                ['id' => $orderId]),
            'items' => array_map(static fn (array $row): array => [
                'id' => (int) $row['id'],
                'materialId' => (int) $row['materialId'],
                'materialCode' => (string) $row['materialCode'],
                'materialName' => (string) $row['materialName'],
                'quantity' => round((float) $row['quantity'], 3),
                'unitPrice' => round((float) $row['unitPrice'], 2),
                'batchNumber' => (string) $row['batchNumber'],
                'expiryDate' => $row['expiryDate'],
                'materialBatchId' => $row['materialBatchId'] === null ? null : (int) $row['materialBatchId'],
                'qualityStatus' => (string) ($row['qualityStatus'] ?? 'QUARANTINE'),
                'batchQuantity' => round((float) ($row['batchQuantity'] ?? 0), 3),
            ], $items),
        ];
    }

    /** @return array<string,mixed> */
    private function order(int $id): array
    {
        $row = $this->db->first(
            'SELECT po.*, s.name AS supplierName, s.code AS supplierCode, s.paymentTerms,
                    (SELECT COUNT(*) FROM purchaseOrderItems i WHERE i.orderId = po.id) AS itemCount,
                    (SELECT COALESCE(SUM(i.receivedQty), 0) FROM purchaseOrderItems i WHERE i.orderId = po.id)
                        AS receivedQuantity,
                    (SELECT COUNT(*) FROM goodsReceipts r WHERE r.orderId = po.id) AS receiptCount
             FROM purchaseOrders po
             JOIN suppliers s ON s.id = po.supplierId
             WHERE po.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Purchase order');
        }

        return $this->hydrateOrder($row);
    }

    private function hydrateOrder(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'orderNumber' => (string) $row['orderNumber'],
            'supplierId' => (int) $row['supplierId'],
            'supplierCode' => (string) ($row['supplierCode'] ?? ''),
            'supplierName' => (string) $row['supplierName'],
            'status' => (string) $row['status'],
            'orderDate' => (string) $row['orderDate'],
            'expectedDate' => $row['expectedDate'],
            'amountHt' => round((float) $row['amountHt'], 2),
            'vatAmount' => round((float) $row['vatAmount'], 2),
            'totalTtc' => round((float) $row['totalTtc'], 2),
            'currency' => (string) $row['currency'],
            'notes' => (string) ($row['notes'] ?? ''),
            'itemCount' => (int) ($row['itemCount'] ?? 0),
            'receivedQuantity' => round((float) ($row['receivedQuantity'] ?? 0), 3),
            'receiptCount' => (int) ($row['receiptCount'] ?? 0),
            'createdAt' => $row['createdAt'] ?? null,
            'updatedAt' => $row['updatedAt'] ?? null,
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
        if ($parsed === false || $parsed->format('Y-m-d') !== trim($value)) {
            throw ApiException::validation(['date' => 'validation.date']);
        }

        return $parsed->format('Y-m-d');
    }
}
