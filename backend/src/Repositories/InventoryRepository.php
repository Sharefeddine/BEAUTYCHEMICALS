<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Warehouses, batches, the stock ledger and FEFO allocation.
 *
 * <p>Stock is never a number on a product: it is the sum of the batch rows, and a batch only
 * changes through this repository, which writes the ledger at the same time. The rules that the
 * database cannot express on its own live here:</p>
 * <ul>
 *   <li><b>FEFO</b> — an allocation always consumes the released batch that expires first;</li>
 *   <li><b>quarantine is one-way</b> — a released batch never enters a quarantine warehouse and a
 *       batch held in quarantine never leaves it for a selling warehouse;</li>
 *   <li><b>no silent stock</b> — every adjustment and transfer names its reason and is written to
 *       the append-only {@code stockMovements} ledger with the user who did it.</li>
 * </ul>
 */
final class InventoryRepository
{
    private const KINDS = ['MATERIAL', 'PRODUCT'];
    private const QUALITIES = ['QUARANTINE', 'RELEASED', 'REJECTED'];
    private const MOVEMENT_TYPES = ['IN', 'OUT', 'TRANSFER', 'ADJUSTMENT', 'RETURN',
        'PRODUCTION_IN', 'PRODUCTION_OUT', 'WASTE'];

    public function __construct(private readonly Database $db)
    {
    }

    /** @return array<int,array> */
    public function warehouses(): array
    {
        $rows = $this->db->select(
            'SELECT id, code, name, address, isQuarantine FROM warehouses
             WHERE deletedAt IS NULL AND isActive = 1 ORDER BY isQuarantine, name'
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'name' => (string) $row['name'],
            'address' => (string) $row['address'],
            'quarantine' => (int) $row['isQuarantine'] === 1,
        ], $rows);
    }

    /**
     * Numbers the inventory screen shows in its header, all computed from the batch rows.
     *
     * @return array<string,mixed>
     */
    public function summary(int $expiryWarningDays): array
    {
        $limit = (new \DateTimeImmutable('today'))->modify('+' . max(0, $expiryWarningDays) . ' days')
            ->format('Y-m-d');
        $material = $this->db->first(
            'SELECT COUNT(*) AS batchCount,
                    COALESCE(SUM(b.quantity), 0) AS quantity,
                    COALESCE(SUM(b.quantity * b.purchasePrice), 0) AS value,
                    COALESCE(SUM(b.qualityStatus = \'QUARANTINE\'), 0) AS quarantineBatches,
                    COALESCE(SUM(b.quantity > 0 AND b.expiryDate IS NOT NULL AND b.expiryDate <= :expiryLimit), 0) AS expiringBatches
             FROM materialBatches b',
            ['expiryLimit' => $limit]
        ) ?? [];
        $product = $this->db->first(
            'SELECT COUNT(*) AS batchCount,
                    COALESCE(SUM(b.quantity), 0) AS quantity,
                    COALESCE(SUM(b.quantity * b.unitCost), 0) AS value,
                    COALESCE(SUM(b.qualityStatus = \'QUARANTINE\'), 0) AS quarantineBatches,
                    COALESCE(SUM(b.quantity > 0 AND b.expiryDate IS NOT NULL AND b.expiryDate <= :expiryLimit), 0) AS expiringBatches
             FROM productBatches b',
            ['expiryLimit' => $limit]
        ) ?? [];

        return [
            'batchCount' => (int) $material['batchCount'] + (int) $product['batchCount'],
            'materialBatchCount' => (int) $material['batchCount'],
            'productBatchCount' => (int) $product['batchCount'],
            'stockValue' => round((float) $material['value'] + (float) $product['value'], 2),
            'materialValue' => round((float) $material['value'], 2),
            'productValue' => round((float) $product['value'], 2),
            'quarantineBatches' => (int) $material['quarantineBatches'] + (int) $product['quarantineBatches'],
            'expiringBatches' => (int) $material['expiringBatches'] + (int) $product['expiringBatches'],
            'expiryWarningDays' => $expiryWarningDays,
            'expiryLimit' => $limit,
            'lowStockItems' => (int) $this->db->scalar('SELECT COUNT(*) FROM vwLowStock'),
            'warehouseCount' => count($this->warehouses()),
            'currency' => 'DZD',
        ];
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateStock(int $page, int $pageSize, string $search, ?int $warehouseId, ?string $kind,
                                  ?string $quality, int $expiringDays, bool $availableOnly): array
    {
        $union = $this->stockUnion();
        $where = ['1 = 1'];
        $params = [];

        if ($search !== '') {
            $where[] = '(itemCode LIKE :searchCode OR itemName LIKE :searchName OR batchNumber LIKE :searchBatch)';
            $params['searchCode'] = '%' . $search . '%';
            $params['searchName'] = '%' . $search . '%';
            $params['searchBatch'] = '%' . $search . '%';
        }
        if ($warehouseId !== null && $warehouseId > 0) {
            $where[] = 'warehouseId = :warehouseId';
            $params['warehouseId'] = $warehouseId;
        }
        if ($kind !== null && $kind !== '' && in_array(strtoupper($kind), self::KINDS, true)) {
            $where[] = 'itemKind = :kind';
            $params['kind'] = strtoupper($kind);
        }
        if ($quality !== null && $quality !== '' && in_array(strtoupper($quality), self::QUALITIES, true)) {
            $where[] = 'qualityStatus = :quality';
            $params['quality'] = strtoupper($quality);
        }
        if ($expiringDays > 0) {
            $where[] = 'quantity > 0 AND expiryDate IS NOT NULL AND expiryDate <= :expiryLimit';
            $params['expiryLimit'] = (new \DateTimeImmutable('today'))
                ->modify('+' . $expiringDays . ' days')->format('Y-m-d');
        }
        if ($availableOnly) {
            $where[] = 'quantity > 0';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM (' . $union . ') AS stock ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT itemKind, batchId, itemId, itemCode, itemName, batchNumber, warehouseId, warehouseName,
                    quantity, unit, baseUnit, productionDate, expiryDate, qualityStatus, unitCost, supplierName
             FROM (' . $union . ') AS stock ' . $clause . '
             ORDER BY expiryDate IS NULL, expiryDate ASC, itemName ASC, batchNumber ASC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateStock'], $rows), 'total' => $total];
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginateMovements(int $page, int $pageSize, ?string $kind, ?int $batchId, ?int $warehouseId,
                                      ?string $type, ?string $from, ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];

        if ($kind !== null && $kind !== '' && in_array(strtoupper($kind), self::KINDS, true)) {
            $where[] = strtoupper($kind) === 'MATERIAL' ? 'sm.materialBatchId IS NOT NULL'
                : 'sm.productBatchId IS NOT NULL';
        }
        if ($batchId !== null && $batchId > 0) {
            if ($kind !== null && strtoupper((string) $kind) === 'PRODUCT') {
                $where[] = 'sm.productBatchId = :batchId';
                $params['batchId'] = $batchId;
            } elseif ($kind !== null && strtoupper((string) $kind) === 'MATERIAL') {
                $where[] = 'sm.materialBatchId = :batchId';
                $params['batchId'] = $batchId;
            } else {
                $where[] = '(sm.materialBatchId = :batchIdMaterial OR sm.productBatchId = :batchIdProduct)';
                $params['batchIdMaterial'] = $batchId;
                $params['batchIdProduct'] = $batchId;
            }
        }
        if ($warehouseId !== null && $warehouseId > 0) {
            $where[] = '(sm.warehouseId = :warehouseId OR sm.targetWarehouseId = :warehouseIdTarget)';
            $params['warehouseId'] = $warehouseId;
            $params['warehouseIdTarget'] = $warehouseId;
        }
        if ($type !== null && $type !== '' && in_array(strtoupper($type), self::MOVEMENT_TYPES, true)) {
            $where[] = 'sm.movementType = :type';
            $params['type'] = strtoupper($type);
        }
        if ($from !== null && $from !== '') {
            $where[] = 'sm.occurredAt >= :from';
            $params['from'] = $from . ' 00:00:00';
        }
        if ($to !== null && $to !== '') {
            $where[] = 'sm.occurredAt <= :to';
            $params['to'] = $to . ' 23:59:59';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM stockMovements sm ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT sm.id, sm.movementNumber, sm.movementType, sm.quantity, sm.unitCost, sm.referenceType,
                    sm.referenceId, sm.reason, sm.occurredAt, sm.warehouseId, w.name AS warehouseName,
                    sm.targetWarehouseId, tw.name AS targetWarehouseName, u.username AS userName,
                    CASE WHEN sm.materialBatchId IS NOT NULL THEN \'MATERIAL\' ELSE \'PRODUCT\' END AS itemKind,
                    COALESCE(mb.batchNumber, pb.batchNumber) AS batchNumber,
                    COALESCE(m.code, p.sku) AS itemCode,
                    COALESCE(m.commercialName, p.nameFr) AS itemName,
                    COALESCE(mb.id, pb.id) AS batchRef
             FROM stockMovements sm
             JOIN warehouses w ON w.id = sm.warehouseId
             LEFT JOIN warehouses tw ON tw.id = sm.targetWarehouseId
             LEFT JOIN users u ON u.id = sm.userId
             LEFT JOIN materialBatches mb ON mb.id = sm.materialBatchId
             LEFT JOIN rawMaterials m ON m.id = mb.materialId
             LEFT JOIN productBatches pb ON pb.id = sm.productBatchId
             LEFT JOIN products p ON p.id = pb.productId
             ' . $clause . '
             ORDER BY sm.occurredAt DESC, sm.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'movementNumber' => (string) $row['movementNumber'],
            'movementType' => (string) $row['movementType'],
            'itemKind' => (string) $row['itemKind'],
            'batchId' => (int) $row['batchRef'],
            'batchNumber' => (string) ($row['batchNumber'] ?? ''),
            'itemCode' => (string) ($row['itemCode'] ?? ''),
            'itemName' => (string) ($row['itemName'] ?? ''),
            'warehouseId' => (int) $row['warehouseId'],
            'warehouseName' => (string) $row['warehouseName'],
            'targetWarehouseId' => $row['targetWarehouseId'] === null ? null : (int) $row['targetWarehouseId'],
            'targetWarehouseName' => $row['targetWarehouseName'] ?? null,
            'quantity' => round((float) $row['quantity'], 3),
            'unitCost' => round((float) $row['unitCost'], 4),
            'value' => round((float) $row['quantity'] * (float) $row['unitCost'], 2),
            'referenceType' => (string) $row['referenceType'],
            'referenceId' => $row['referenceId'] === null ? null : (int) $row['referenceId'],
            'reason' => (string) $row['reason'],
            'userName' => (string) ($row['userName'] ?? ''),
            'occurredAt' => (string) $row['occurredAt'],
        ], $rows), 'total' => $total];
    }

    /**
     * FEFO proposal: which released batches would serve a quantity, earliest expiry first.
     * Read-only — nothing is consumed, so the screen can show the plan before it is confirmed.
     *
     * @return array<string,mixed>
     */
    public function fefo(string $kind, int $itemId, float $quantity): array
    {
        $kind = strtoupper($kind);
        if (!in_array($kind, self::KINDS, true)) {
            throw ApiException::validation(['kind' => 'validation.allowed_values']);
        }
        if ($quantity <= 0) {
            throw ApiException::validation(['quantity' => 'validation.range']);
        }

        $item = $this->item($kind, $itemId);
        $batches = $this->releasedBatches($kind, $itemId);

        $remaining = $quantity;
        $allocation = [];
        foreach ($batches as $batch) {
            if ($remaining <= 0.0001) {
                break;
            }
            $available = (float) $batch['quantity'];
            if ($available <= 0) {
                continue;
            }
            $take = min($available, $remaining);
            $remaining -= $take;
            $allocation[] = [
                'batchId' => (int) $batch['id'],
                'batchNumber' => (string) $batch['batchNumber'],
                'warehouseId' => (int) $batch['warehouseId'],
                'warehouseName' => (string) $batch['warehouseName'],
                'expiryDate' => $batch['expiryDate'],
                'available' => round($available, 3),
                'take' => round($take, 3),
                'unitCost' => round((float) $batch['unitCost'], 4),
            ];
        }

        $allocated = round($quantity - max(0.0, $remaining), 3);

        return [
            'itemKind' => $kind,
            'itemId' => $itemId,
            'itemCode' => $item['code'],
            'itemName' => $item['name'],
            'unit' => $item['unit'],
            'requested' => round($quantity, 3),
            'allocated' => $allocated,
            'shortfall' => round(max(0.0, $remaining), 3),
            'sufficient' => $remaining <= 0.0001,
            'batches' => $allocation,
        ];
    }

    /**
     * Physical count: the batch is set to the counted quantity and the difference is written as
     * an ADJUSTMENT movement, so the ledger explains every gram that appears or disappears.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function adjust(array $data, int $userId): array
    {
        $kind = strtoupper((string) ($data['itemKind'] ?? 'MATERIAL'));
        $batchId = (int) ($data['batchId'] ?? 0);
        $counted = round((float) ($data['countedQuantity'] ?? 0), 3);
        $reason = trim((string) ($data['reason'] ?? ''));
        if ($counted < 0) {
            throw ApiException::validation(['countedQuantity' => 'validation.range']);
        }
        if ($reason === '') {
            throw ApiException::validation(['reason' => 'validation.required']);
        }

        return $this->db->transaction(function () use ($kind, $batchId, $counted, $reason, $userId): array {
            $batch = $this->batch($kind, $batchId);
            $current = round((float) $batch['quantity'], 3);
            $delta = round($counted - $current, 3);
            if (abs($delta) < 0.0001) {
                throw ApiException::conflict('The counted quantity matches the system quantity: nothing to adjust.',
                    'NO_ADJUSTMENT');
            }

            $table = $kind === 'MATERIAL' ? 'materialBatches' : 'productBatches';
            $this->db->execute('UPDATE ' . $table . ' SET quantity = :quantity WHERE id = :id',
                ['quantity' => $counted, 'id' => $batchId]);

            $movementId = $this->postMovement([
                'movementType' => 'ADJUSTMENT',
                'warehouseId' => (int) $batch['warehouseId'],
                'targetWarehouseId' => null,
                'materialBatchId' => $kind === 'MATERIAL' ? $batchId : null,
                'productBatchId' => $kind === 'PRODUCT' ? $batchId : null,
                'quantity' => abs($delta),
                'unitCost' => (float) $batch['unitCost'],
                'referenceType' => 'STOCK_COUNT',
                'referenceId' => null,
                'reason' => $reason,
                'userId' => $userId,
            ]);

            return [
                'movementId' => $movementId,
                'movementType' => 'ADJUSTMENT',
                'itemKind' => $kind,
                'batchId' => $batchId,
                'batchNumber' => (string) $batch['batchNumber'],
                'previousQuantity' => $current,
                'countedQuantity' => $counted,
                'delta' => $delta,
                'unit' => (string) $batch['unit'],
                'reason' => $reason,
            ];
        });
    }

    /**
     * Moves part of a batch to another warehouse. The batch keeps its number, its expiry and its
     * quality status: a transfer is a location change, never a quality decision.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function transfer(array $data, int $userId): array
    {
        $kind = strtoupper((string) ($data['itemKind'] ?? 'MATERIAL'));
        $batchId = (int) ($data['batchId'] ?? 0);
        $targetWarehouseId = (int) ($data['targetWarehouseId'] ?? 0);
        $quantity = round((float) ($data['quantity'] ?? 0), 3);
        $reason = trim((string) ($data['reason'] ?? ''));
        if ($quantity <= 0) {
            throw ApiException::validation(['quantity' => 'validation.range']);
        }
        if ($reason === '') {
            throw ApiException::validation(['reason' => 'validation.required']);
        }

        return $this->db->transaction(function () use ($kind, $batchId, $targetWarehouseId, $quantity, $reason, $userId): array {
            $batch = $this->batch($kind, $batchId);
            $available = round((float) $batch['quantity'], 3);
            if ($quantity > $available) {
                throw ApiException::conflict('The batch holds ' . $available . ' ' . $batch['unit']
                    . ': the transfer cannot exceed it.', 'INSUFFICIENT_STOCK');
            }

            $target = $this->db->first('SELECT id, name, isQuarantine FROM warehouses
                                        WHERE id = :id AND deletedAt IS NULL AND isActive = 1',
                ['id' => $targetWarehouseId]);
            if ($target === null) {
                throw ApiException::notFound('Warehouse');
            }
            if ((int) $target['id'] === (int) $batch['warehouseId']) {
                throw ApiException::validation(['targetWarehouseId' => 'inventory.error.same_warehouse']);
            }

            $quality = (string) $batch['qualityStatus'];
            if ((int) $target['isQuarantine'] === 1 && $quality !== 'QUARANTINE') {
                throw ApiException::conflict('Only goods held in quarantine may enter the quarantine warehouse.',
                    'QUARANTINE_RULE');
            }
            if ((int) $target['isQuarantine'] === 0 && $quality !== 'RELEASED') {
                throw ApiException::conflict('Only released goods may enter a selling warehouse: release the batch first.',
                    'QUARANTINE_RULE');
            }

            $table = $kind === 'MATERIAL' ? 'materialBatches' : 'productBatches';
            $this->db->execute('UPDATE ' . $table . ' SET quantity = quantity - :quantity WHERE id = :id',
                ['quantity' => $quantity, 'id' => $batchId]);

            $targetBatchId = $this->upsertTargetBatch($kind, $batch, $targetWarehouseId, $quantity);

            $movementId = $this->postMovement([
                'movementType' => 'TRANSFER',
                'warehouseId' => (int) $batch['warehouseId'],
                'targetWarehouseId' => $targetWarehouseId,
                'materialBatchId' => $kind === 'MATERIAL' ? $batchId : null,
                'productBatchId' => $kind === 'PRODUCT' ? $batchId : null,
                'quantity' => $quantity,
                'unitCost' => (float) $batch['unitCost'],
                'referenceType' => 'TRANSFER',
                'referenceId' => null,
                'reason' => $reason,
                'userId' => $userId,
            ]);

            return [
                'movementId' => $movementId,
                'movementType' => 'TRANSFER',
                'itemKind' => $kind,
                'batchId' => $batchId,
                'batchNumber' => (string) $batch['batchNumber'],
                'quantity' => $quantity,
                'unit' => (string) $batch['unit'],
                'sourceWarehouseId' => (int) $batch['warehouseId'],
                'sourceWarehouseName' => (string) $batch['warehouseName'],
                'targetWarehouseId' => $targetWarehouseId,
                'targetWarehouseName' => (string) $target['name'],
                'targetBatchId' => $targetBatchId,
                'remainingSourceQuantity' => round($available - $quantity, 3),
            ];
        });
    }

    // ------------------------------------------------------------------ internals

    private function stockUnion(): string
    {
        return "SELECT 'MATERIAL' AS itemKind, b.id AS batchId, m.id AS itemId, m.code AS itemCode,
                       m.commercialName AS itemName, b.batchNumber, b.warehouseId, w.name AS warehouseName,
                       b.quantity, b.unit, b.productionDate, b.expiryDate, b.qualityStatus,
                       b.purchasePrice AS unitCost, s.name AS supplierName, m.storageUnit AS baseUnit
                FROM materialBatches b
                JOIN rawMaterials m ON m.id = b.materialId
                JOIN warehouses w ON w.id = b.warehouseId
                LEFT JOIN suppliers s ON s.id = b.supplierId
                UNION ALL
                SELECT 'PRODUCT', b.id, p.id, p.sku, p.nameFr, b.batchNumber, b.warehouseId, w.name,
                       b.quantity, b.unit, b.productionDate, b.expiryDate, b.qualityStatus,
                       b.unitCost, NULL, p.unit
                FROM productBatches b
                JOIN products p ON p.id = b.productId
                JOIN warehouses w ON w.id = b.warehouseId";
    }

    /** @return array<string,mixed> */
    private function hydrateStock(array $row): array
    {
        $quantity = round((float) $row['quantity'], 3);
        $expiry = $row['expiryDate'] === null ? null : (string) $row['expiryDate'];
        $daysToExpiry = null;
        if ($expiry !== null) {
            $today = new \DateTimeImmutable('today');
            $daysToExpiry = (int) $today->diff(new \DateTimeImmutable($expiry))->format('%r%a');
        }

        return [
            'itemKind' => (string) $row['itemKind'],
            'batchId' => (int) $row['batchId'],
            'itemId' => (int) $row['itemId'],
            'itemCode' => (string) $row['itemCode'],
            'itemName' => (string) $row['itemName'],
            'batchNumber' => (string) $row['batchNumber'],
            'warehouseId' => (int) $row['warehouseId'],
            'warehouseName' => (string) $row['warehouseName'],
            'quantity' => $quantity,
            'unit' => (string) $row['unit'],
            'baseUnit' => (string) ($row['baseUnit'] ?? $row['unit']),
            'productionDate' => $row['productionDate'],
            'expiryDate' => $expiry,
            'daysToExpiry' => $daysToExpiry,
            'expired' => $daysToExpiry !== null && $daysToExpiry < 0,
            'qualityStatus' => (string) $row['qualityStatus'],
            'unitCost' => round((float) $row['unitCost'], 4),
            'stockValue' => round($quantity * (float) $row['unitCost'], 2),
            'supplierName' => $row['supplierName'] ?? null,
        ];
    }

    /** @return array{code:string,name:string,unit:string} */
    private function item(string $kind, int $itemId): array
    {
        if ($kind === 'MATERIAL') {
            $row = $this->db->first('SELECT code, commercialName AS name, storageUnit AS unit
                                     FROM rawMaterials WHERE id = :id AND deletedAt IS NULL', ['id' => $itemId]);
        } else {
            $row = $this->db->first('SELECT sku AS code, nameFr AS name, unit
                                     FROM products WHERE id = :id AND deletedAt IS NULL', ['id' => $itemId]);
        }
        if ($row === null) {
            throw ApiException::notFound($kind === 'MATERIAL' ? 'Raw material' : 'Product');
        }

        return ['code' => (string) $row['code'], 'name' => (string) $row['name'], 'unit' => (string) $row['unit']];
    }

    /** @return array<int,array> */
    private function releasedBatches(string $kind, int $itemId): array
    {
        if ($kind === 'MATERIAL') {
            return $this->db->select(
                'SELECT b.id, b.batchNumber, b.warehouseId, b.quantity, b.expiryDate,
                        b.purchasePrice AS unitCost, w.name AS warehouseName
                 FROM materialBatches b
                 JOIN warehouses w ON w.id = b.warehouseId
                 WHERE b.materialId = :itemId AND b.qualityStatus = \'RELEASED\' AND b.quantity > 0
                 ORDER BY b.expiryDate IS NULL, b.expiryDate ASC, b.batchNumber ASC',
                ['itemId' => $itemId]
            );
        }

        return $this->db->select(
            'SELECT b.id, b.batchNumber, b.warehouseId, b.quantity, b.expiryDate,
                    b.unitCost, w.name AS warehouseName
             FROM productBatches b
             JOIN warehouses w ON w.id = b.warehouseId
             WHERE b.productId = :itemId AND b.qualityStatus = \'RELEASED\' AND b.quantity > 0
             ORDER BY b.expiryDate IS NULL, b.expiryDate ASC, b.batchNumber ASC',
            ['itemId' => $itemId]
        );
    }

    /** @return array<string,mixed> */
    private function batch(string $kind, int $batchId): array
    {
        if ($kind === 'MATERIAL') {
            $row = $this->db->first(
                'SELECT b.*, w.name AS warehouseName, b.purchasePrice AS unitCost, m.id AS itemId,
                        m.code AS itemCode, m.commercialName AS itemName
                 FROM materialBatches b
                 JOIN warehouses w ON w.id = b.warehouseId
                 JOIN rawMaterials m ON m.id = b.materialId
                 WHERE b.id = :id',
                ['id' => $batchId]
            );
        } else {
            $row = $this->db->first(
                'SELECT b.*, w.name AS warehouseName, b.unitCost, p.id AS itemId,
                        p.sku AS itemCode, p.nameFr AS itemName
                 FROM productBatches b
                 JOIN warehouses w ON w.id = b.warehouseId
                 JOIN products p ON p.id = b.productId
                 WHERE b.id = :id',
                ['id' => $batchId]
            );
        }
        if ($row === null) {
            throw ApiException::notFound('Batch');
        }

        return $row;
    }

    private function upsertTargetBatch(string $kind, array $batch, int $targetWarehouseId, float $quantity): int
    {
        if ($kind === 'MATERIAL') {
            $existing = $this->db->first(
                'SELECT id FROM materialBatches WHERE materialId = :itemId AND batchNumber = :batchNumber
                 AND warehouseId = :warehouseId',
                ['itemId' => (int) $batch['itemId'], 'batchNumber' => (string) $batch['batchNumber'],
                    'warehouseId' => $targetWarehouseId]
            );
        } else {
            $existing = $this->db->first(
                'SELECT id FROM productBatches WHERE productId = :itemId AND batchNumber = :batchNumber
                 AND warehouseId = :warehouseId',
                ['itemId' => (int) $batch['itemId'], 'batchNumber' => (string) $batch['batchNumber'],
                    'warehouseId' => $targetWarehouseId]
            );
        }

        if ($existing !== null) {
            $targetId = (int) $existing['id'];
            $table = $kind === 'MATERIAL' ? 'materialBatches' : 'productBatches';
            $this->db->execute('UPDATE ' . $table . ' SET quantity = quantity + :quantity WHERE id = :id',
                ['quantity' => $quantity, 'id' => $targetId]);

            return $targetId;
        }

        if ($kind === 'MATERIAL') {
            $this->db->execute(
                'INSERT INTO materialBatches (materialId, batchNumber, warehouseId, supplierId, quantity, unit,
                        productionDate, expiryDate, qualityStatus, purchasePrice, releasedAt, releasedBy)
                 VALUES (:materialId, :batchNumber, :warehouseId, :supplierId, :quantity, :unit,
                        :productionDate, :expiryDate, :qualityStatus, :purchasePrice, :releasedAt, :releasedBy)',
                [
                    'materialId' => (int) $batch['itemId'],
                    'batchNumber' => (string) $batch['batchNumber'],
                    'warehouseId' => $targetWarehouseId,
                    'supplierId' => $batch['supplierId'] === null ? null : (int) $batch['supplierId'],
                    'quantity' => $quantity,
                    'unit' => (string) $batch['unit'],
                    'productionDate' => $batch['productionDate'],
                    'expiryDate' => $batch['expiryDate'],
                    'qualityStatus' => (string) $batch['qualityStatus'],
                    'purchasePrice' => (float) $batch['purchasePrice'],
                    'releasedAt' => $batch['releasedAt'],
                    'releasedBy' => $batch['releasedBy'] === null ? null : (int) $batch['releasedBy'],
                ]
            );
        } else {
            $this->db->execute(
                'INSERT INTO productBatches (productId, batchNumber, warehouseId, productionOrderId, quantity,
                        unit, productionDate, expiryDate, qualityStatus, unitCost)
                 VALUES (:productId, :batchNumber, :warehouseId, :productionOrderId, :quantity,
                        :unit, :productionDate, :expiryDate, :qualityStatus, :unitCost)',
                [
                    'productId' => (int) $batch['itemId'],
                    'batchNumber' => (string) $batch['batchNumber'],
                    'warehouseId' => $targetWarehouseId,
                    'productionOrderId' => $batch['productionOrderId'] === null ? null : (int) $batch['productionOrderId'],
                    'quantity' => $quantity,
                    'unit' => (string) $batch['unit'],
                    'productionDate' => $batch['productionDate'],
                    'expiryDate' => $batch['expiryDate'],
                    'qualityStatus' => (string) $batch['qualityStatus'],
                    'unitCost' => (float) $batch['unitCost'],
                ]
            );
        }

        return $this->db->lastId();
    }

    /** @param array<string,mixed> $movement */
    public function postMovement(array $movement): int
    {
        $this->db->execute(
            'INSERT INTO stockMovements (movementNumber, movementType, warehouseId, targetWarehouseId,
                    materialBatchId, productBatchId, quantity, unitCost, referenceType, referenceId, reason, userId)
             VALUES (:movementNumber, :movementType, :warehouseId, :targetWarehouseId,
                    :materialBatchId, :productBatchId, :quantity, :unitCost, :referenceType, :referenceId,
                    :reason, :userId)',
            [
                'movementNumber' => $this->nextMovementNumber(),
                'movementType' => (string) $movement['movementType'],
                'warehouseId' => (int) $movement['warehouseId'],
                'targetWarehouseId' => $movement['targetWarehouseId'] ?? null,
                'materialBatchId' => $movement['materialBatchId'] ?? null,
                'productBatchId' => $movement['productBatchId'] ?? null,
                'quantity' => round((float) $movement['quantity'], 3),
                'unitCost' => round((float) ($movement['unitCost'] ?? 0), 4),
                'referenceType' => (string) ($movement['referenceType'] ?? ''),
                'referenceId' => $movement['referenceId'] ?? null,
                'reason' => (string) ($movement['reason'] ?? ''),
                'userId' => (int) $movement['userId'],
            ]
        );

        return $this->db->lastId();
    }

    private function nextMovementNumber(): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM stockMovements') + 1;
        do {
            $number = sprintf('MVT-%05d', $sequence);
            $exists = $this->db->first('SELECT id FROM stockMovements WHERE movementNumber = :number',
                ['number' => $number]) !== null;
            $sequence++;
        } while ($exists);

        return $number;
    }
}
