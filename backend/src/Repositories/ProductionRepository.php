<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Manufacturing orders, their material consumption, quality release and batch genealogy.
 *
 * <p>Production is the only place where raw material stock is consumed and finished goods appear:</p>
 * <ul>
 *   <li>an order is built from an <b>approved formulation version</b> — the application never
 *       invents a formula, it scales the quantities written by the production manager;</li>
 *   <li>starting an order reserves released batches in FEFO order from the order's warehouse; a
 *       shortfall puts the order in WAITING_MATERIALS instead of pretending it can run;</li>
 *   <li>consumption is what the operator actually used, batch by batch, and every gram leaves
 *       through a PRODUCTION_OUT movement; over-consumption is recorded, never refused, because
 *       reality is not a validation rule — the deviation is reported as a variance;</li>
 *   <li>completing an order creates the finished batch in QUARANTINE; only a PASS quality check
 *       releases it, so nothing unsold but unreleased can ever be sold;</li>
 *   <li>genealogy walks finished batch → consumed material batches → supplier receptions.</li>
 * </ul>
 */
final class ProductionRepository
{
    private const STATUSES = ['DRAFT', 'WAITING_MATERIALS', 'APPROVED', 'IN_PROGRESS', 'QUALITY_CONTROL',
        'COMPLETED', 'REJECTED', 'CANCELLED'];

    public function __construct(private readonly Database $db, private readonly InventoryRepository $inventory)
    {
    }

    /** Approved, locked versions the shop floor may produce — the only source of a formula. */
    public function versions(): array
    {
        $rows = $this->db->select(
            'SELECT v.id, v.version, v.expectedYield, v.yieldUnit, v.instructions,
                    f.id AS formulationId, f.code AS formulationCode, f.nameFr, f.nameAr, f.productId,
                    p.sku AS productCode, p.nameFr AS productName, p.type AS productType,
                    (SELECT COUNT(*) FROM formulationItems i WHERE i.versionId = v.id) AS itemCount
             FROM formulationVersions v
             JOIN formulations f ON f.id = v.formulationId
             LEFT JOIN products p ON p.id = f.productId
             WHERE v.isLocked = 1 AND f.deletedAt IS NULL
             ORDER BY f.code, v.version DESC'
        );

        return array_map(static fn (array $row): array => [
            'versionId' => (int) $row['id'],
            'version' => (int) $row['version'],
            'formulationId' => (int) $row['formulationId'],
            'formulationCode' => (string) $row['formulationCode'],
            'nameFr' => (string) $row['nameFr'],
            'nameAr' => (string) $row['nameAr'],
            'productId' => (int) ($row['productId'] ?? 0),
            'productCode' => (string) ($row['productCode'] ?? ''),
            'productName' => (string) ($row['productName'] ?? ''),
            'productType' => (string) ($row['productType'] ?? ''),
            'expectedYield' => round((float) $row['expectedYield'], 3),
            'yieldUnit' => (string) $row['yieldUnit'],
            'instructions' => (string) ($row['instructions'] ?? ''),
            'itemCount' => (int) $row['itemCount'],
        ], $rows);
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, ?string $status, ?string $from,
                             ?string $to): array
    {
        $where = ['1 = 1'];
        $params = [];

        if ($search !== '') {
            // PDO runs with server-side prepares: a named placeholder may appear only once, so the
            // same term is bound under four names.
            $where[] = '(po.orderNumber LIKE :searchNumber OR p.sku LIKE :searchProduct
                         OR p.nameFr LIKE :searchName OR f.code LIKE :searchFormulation)';
            $term = '%' . $search . '%';
            $params['searchNumber'] = $term;
            $params['searchProduct'] = $term;
            $params['searchName'] = $term;
            $params['searchFormulation'] = $term;
        }
        if ($status !== null && $status !== '' && in_array(strtoupper($status), self::STATUSES, true)) {
            $where[] = 'po.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($from !== null && $from !== '') {
            $where[] = 'po.plannedDate >= :from';
            $params['from'] = $from;
        }
        if ($to !== null && $to !== '') {
            $where[] = 'po.plannedDate <= :to';
            $params['to'] = $to;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM productionOrders po
             JOIN products p ON p.id = po.productId
             LEFT JOIN formulationVersions v ON v.id = po.formulationVersionId
             LEFT JOIN formulations f ON f.id = v.formulationId ' . $clause,
            $params
        );

        $rows = $this->db->select(
            'SELECT po.*, p.sku AS productCode, p.nameFr AS productName, w.code AS warehouseCode,
                    w.name AS warehouseName, f.code AS formulationCode, v.version AS formulationVersion,
                    (SELECT COUNT(*) FROM productionMaterials m WHERE m.orderId = po.id) AS materialLines,
                    (SELECT COALESCE(SUM(m.consumedQuantity), 0) FROM productionMaterials m
                        WHERE m.orderId = po.id) AS consumedQuantity,
                    (SELECT COUNT(*) FROM qualityChecks q WHERE q.orderId = po.id) AS qualityCheckCount,
                    (SELECT COALESCE(SUM(o.quantity), 0) FROM productionOutputs o WHERE o.orderId = po.id)
                        AS outputQuantity
             FROM productionOrders po
             JOIN products p ON p.id = po.productId
             JOIN warehouses w ON w.id = po.warehouseId
             LEFT JOIN formulationVersions v ON v.id = po.formulationVersionId
             LEFT JOIN formulations f ON f.id = v.formulationId
             ' . $clause . '
             ORDER BY po.plannedDate DESC, po.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateOrder'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function find(int $id): array
    {
        $order = $this->order($id);
        $plan = $this->plan((int) $order['formulationVersionId'], (float) $order['plannedQuantity']);
        $allocations = $this->allocations($id);
        $outputs = $this->outputs($id);
        $checks = $this->checks($id);

        $standard = 0.0;
        foreach ($plan as $line) {
            $standard += $line['plannedQuantity'] * $line['unitPrice'];
        }

        $consumed = array_sum(array_column($allocations, 'consumedQuantity'));
        $materialCost = round((float) $order['materialCost'], 2);
        $totalCost = round($materialCost + (float) $order['packagingCost'] + (float) $order['otherCost'], 2);
        $actual = (float) $order['actualQuantity'];
        $variance = round($materialCost - $standard, 2);

        $order['plan'] = $plan;
        $order['materials'] = $allocations;
        $order['outputs'] = $outputs;
        $order['qualityChecks'] = $checks;
        $order['consumedQuantity'] = round($consumed, 3);
        $order['standardMaterialCost'] = round($standard, 2);
        $order['totalCost'] = $totalCost;
        $order['costPerUnit'] = $actual > 0 ? round($totalCost / $actual, 2) : 0.0;
        $order['variance'] = $variance;
        $order['variancePct'] = $standard > 0 ? round($variance / $standard * 100, 2) : 0.0;
        $order['sellable'] = $outputs !== [] && array_reduce(
            $outputs, static fn (bool $carry, array $output): bool => $carry && $output['qualityStatus'] === 'RELEASED',
            true
        );

        return $order;
    }

    /**
     * Creates a manufacturing order from an approved version. Nothing is consumed and no batch is
     * created here: the order is a plan the shop floor executes.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function create(array $data, int $userId): array
    {
        $versionId = (int) ($data['formulationVersionId'] ?? 0);
        $warehouseId = (int) ($data['warehouseId'] ?? 0);
        $planned = round((float) ($data['plannedQuantity'] ?? 0), 3);
        $plannedDate = $this->date($data['plannedDate'] ?? null) ?? (new \DateTimeImmutable('today'))->format('Y-m-d');

        if ($planned <= 0) {
            throw ApiException::validation(['plannedQuantity' => 'validation.range']);
        }

        $version = $this->db->first(
            'SELECT v.id, v.version, v.isLocked, v.expectedYield, v.yieldUnit, f.id AS formulationId,
                    f.code AS formulationCode, f.productId, f.status AS formulationStatus
             FROM formulationVersions v
             JOIN formulations f ON f.id = v.formulationId
             WHERE v.id = :id AND f.deletedAt IS NULL',
            ['id' => $versionId]
        );
        if ($version === null) {
            throw ApiException::notFound('Formulation version');
        }
        if ((int) $version['isLocked'] !== 1) {
            throw ApiException::conflict('Only an approved formulation version can be produced.',
                'PRODUCTION_VERSION_NOT_APPROVED');
        }
        if (($version['formulationStatus'] ?? '') === 'ARCHIVED') {
            throw ApiException::conflict('An archived formulation cannot be produced.',
                'PRODUCTION_ARCHIVED');
        }
        $productId = (int) ($version['productId'] ?? 0);
        if ($productId <= 0) {
            throw ApiException::conflict('The formulation is not attached to a finished product.',
                'PRODUCTION_NO_PRODUCT');
        }

        $warehouse = $this->warehouse($warehouseId);
        if ((int) $warehouse['isQuarantine'] === 1) {
            throw ApiException::conflict('A production order cannot consume from the quarantine warehouse.',
                'PRODUCTION_QUARANTINE');
        }

        $plan = $this->plan($versionId, $planned);
        if ($plan === []) {
            throw ApiException::conflict('The formulation version has no raw material: nothing to produce.',
                'PRODUCTION_NO_MATERIALS');
        }

        $id = $this->db->transaction(function () use ($versionId, $productId, $warehouseId, $planned, $plannedDate,
            $data, $userId, $version): int {
            $this->db->execute(
                'INSERT INTO productionOrders (orderNumber, formulationVersionId, productId, warehouseId,
                        plannedQuantity, unit, status, plannedDate, notes, createdBy, updatedBy)
                 VALUES (:orderNumber, :versionId, :productId, :warehouseId, :plannedQuantity, :unit,
                        :status, :plannedDate, :notes, :createdBy, :updatedBy)',
                [
                    'orderNumber' => $this->nextNumber(),
                    'versionId' => $versionId,
                    'productId' => $productId,
                    'warehouseId' => $warehouseId,
                    'plannedQuantity' => $planned,
                    'unit' => (string) $version['yieldUnit'],
                    'status' => 'DRAFT',
                    'plannedDate' => $plannedDate,
                    'notes' => mb_substr(trim((string) ($data['notes'] ?? '')), 0, 255),
                    'createdBy' => $userId,
                    'updatedBy' => $userId,
                ]
            );

            return $this->db->lastId();
        });

        return $this->find($id);
    }

    /**
     * Reserves the released batches the order needs, earliest expiry first, inside the order's
     * warehouse. A shortfall leaves the order in WAITING_MATERIALS — the shop floor sees what is
     * missing instead of an order that cannot start.
     *
     * @return array<string,mixed>
     */
    public function start(int $id, int $userId): array
    {
        $order = $this->order($id);
        if (!in_array($order['status'], ['DRAFT', 'WAITING_MATERIALS', 'APPROVED'], true)) {
            throw ApiException::conflict('Only a draft or waiting order can be started.', 'PRODUCTION_INVALID_STATE');
        }
        if ($this->allocations($id) !== []) {
            throw ApiException::conflict('The materials of this order are already reserved.',
                'PRODUCTION_ALREADY_STARTED');
        }

        $plan = $this->plan((int) $order['formulationVersionId'], (float) $order['plannedQuantity']);
        $shortfalls = [];
        $allocation = [];
        foreach ($plan as $line) {
            $batches = $this->db->select(
                'SELECT id, batchNumber, quantity, unit, purchasePrice, expiryDate
                 FROM materialBatches
                 WHERE materialId = :materialId AND warehouseId = :warehouseId
                   AND qualityStatus = \'RELEASED\' AND quantity > 0
                 ORDER BY expiryDate IS NULL, expiryDate, id',
                ['materialId' => $line['materialId'], 'warehouseId' => (int) $order['warehouseId']]
            );
            $remaining = $line['plannedQuantity'];
            $available = 0.0;
            foreach ($batches as $batch) {
                $available += (float) $batch['quantity'];
                if ($remaining <= 0.0001) {
                    continue;
                }
                $take = min((float) $batch['quantity'], $remaining);
                $remaining -= $take;
                $allocation[] = [
                    'materialBatchId' => (int) $batch['id'],
                    'plannedQuantity' => round($take, 3),
                    'unit' => (string) $batch['unit'],
                ];
            }
            if ($remaining > 0.0001) {
                $shortfalls[] = [
                    'materialId' => $line['materialId'],
                    'materialCode' => $line['materialCode'],
                    'materialName' => $line['materialName'],
                    'required' => $line['plannedQuantity'],
                    'available' => round($available, 3),
                    'missing' => round($remaining, 3),
                    'unit' => $line['unit'],
                ];
            }
        }

        if ($shortfalls !== []) {
            $this->db->execute(
                'UPDATE productionOrders SET status = :status, updatedBy = :userId WHERE id = :id',
                ['status' => 'WAITING_MATERIALS', 'userId' => $userId, 'id' => $id]
            );
            throw new ApiException(409, 'PRODUCTION_MATERIALS',
                'Released stock is missing for this order; it is now waiting for materials.',
                ['shortfalls' => $shortfalls]);
        }

        $this->db->transaction(function () use ($id, $allocation, $userId): void {
            foreach ($allocation as $line) {
                $this->db->execute(
                    'INSERT INTO productionMaterials (orderId, materialBatchId, plannedQuantity, consumedQuantity, unit)
                     VALUES (:orderId, :materialBatchId, :plannedQuantity, 0, :unit)',
                    $line + ['orderId' => $id]
                );
            }
            $this->db->execute(
                'UPDATE productionOrders SET status = :status, startedAt = NOW(), updatedBy = :userId WHERE id = :id',
                ['status' => 'IN_PROGRESS', 'userId' => $userId, 'id' => $id]
            );
        });

        return $this->find($id);
    }

    /**
     * Records what the operator actually consumed, batch by batch, and writes the PRODUCTION_OUT
     * movement that takes it out of stock. Over-consumption is accepted and reported as a variance
     * on the material line, because refusing reality only pushes it into a spreadsheet.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function consume(int $id, array $data, int $userId): array
    {
        $order = $this->order($id);
        if ($order['status'] !== 'IN_PROGRESS') {
            throw ApiException::conflict('Only an order in progress can consume material.',
                'PRODUCTION_INVALID_STATE');
        }

        $lines = is_array($data['items'] ?? null) ? $data['items'] : [];
        if ($lines === []) {
            throw ApiException::validation(['items' => 'validation.required']);
        }

        $allocated = [];
        foreach ($this->allocations($id) as $row) {
            $allocated[$row['materialBatchId']] = $row;
        }

        $prepared = [];
        foreach (array_values($lines) as $index => $line) {
            $batchId = (int) ($line['materialBatchId'] ?? 0);
            $quantity = round((float) ($line['quantity'] ?? 0), 3);
            if ($quantity <= 0) {
                throw ApiException::validation(['items.' . $index . '.quantity' => 'validation.range']);
            }
            if (!isset($allocated[$batchId])) {
                throw ApiException::validation(['items.' . $index . '.materialBatchId' => 'validation.exists']);
            }
            $prepared[] = ['materialBatchId' => $batchId, 'quantity' => $quantity];
        }

        $this->db->transaction(function () use ($prepared, $id, $userId): void {
            foreach ($prepared as $line) {
                $batch = $this->db->first(
                    'SELECT id, batchNumber, warehouseId, quantity, purchasePrice FROM materialBatches
                     WHERE id = :id',
                    ['id' => $line['materialBatchId']]
                );
                if ($batch === null) {
                    throw ApiException::notFound('Material batch');
                }
                $available = round((float) $batch['quantity'], 3);
                if ($line['quantity'] > $available + 0.0001) {
                    throw ApiException::conflict(
                        'Batch ' . (string) $batch['batchNumber'] . ' holds ' . $available . ', not '
                        . $line['quantity'] . '.',
                        'PRODUCTION_STOCK'
                    );
                }
                $this->db->execute(
                    'UPDATE materialBatches SET quantity = quantity - :quantity WHERE id = :id',
                    ['quantity' => $line['quantity'], 'id' => $line['materialBatchId']]
                );
                $this->db->execute(
                    'UPDATE productionMaterials SET consumedQuantity = consumedQuantity + :quantity
                     WHERE orderId = :orderId AND materialBatchId = :materialBatchId',
                    ['quantity' => $line['quantity'], 'orderId' => $id,
                        'materialBatchId' => $line['materialBatchId']]
                );
                $this->inventory->postMovement([
                    'movementType' => 'PRODUCTION_OUT',
                    'warehouseId' => (int) $batch['warehouseId'],
                    'targetWarehouseId' => null,
                    'materialBatchId' => (int) $batch['id'],
                    'productBatchId' => null,
                    'quantity' => $line['quantity'],
                    'unitCost' => (float) $batch['purchasePrice'],
                    'referenceType' => 'PRODUCTION_ORDER',
                    'referenceId' => $id,
                    'reason' => 'Consommation atelier',
                    'userId' => $userId,
                ]);
            }

            $cost = (float) $this->db->scalar(
                'SELECT COALESCE(SUM(m.consumedQuantity * b.purchasePrice), 0)
                 FROM productionMaterials m JOIN materialBatches b ON b.id = m.materialBatchId
                 WHERE m.orderId = :id',
                ['id' => $id]
            );
            $this->db->execute(
                'UPDATE productionOrders SET materialCost = :cost, updatedBy = :userId WHERE id = :id',
                ['cost' => round($cost, 2), 'userId' => $userId, 'id' => $id]
            );
        });

        return $this->find($id);
    }

    /**
     * Closes the order: the finished batch is created in QUARANTINE with the real cost of the run,
     * and the order waits for the quality decision that makes the batch sellable.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function complete(int $id, array $data, int $userId): array
    {
        $order = $this->order($id);
        if ($order['status'] !== 'IN_PROGRESS') {
            throw ApiException::conflict('Only an order in progress can be completed.',
                'PRODUCTION_INVALID_STATE');
        }

        $actual = round((float) ($data['actualQuantity'] ?? 0), 3);
        $waste = round((float) ($data['wasteQuantity'] ?? 0), 3);
        $packaging = round((float) ($data['packagingCost'] ?? 0), 2);
        $other = round((float) ($data['otherCost'] ?? 0), 2);
        if ($actual <= 0) {
            throw ApiException::validation(['actualQuantity' => 'validation.range']);
        }
        if ($waste < 0 || $packaging < 0 || $other < 0) {
            throw ApiException::validation(['wasteQuantity' => 'validation.range']);
        }

        $consumed = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(consumedQuantity), 0) FROM productionMaterials WHERE orderId = :id',
            ['id' => $id]
        );
        if ($consumed <= 0.0001) {
            throw ApiException::conflict('No material has been consumed yet: the order cannot be completed.',
                'PRODUCTION_NO_CONSUMPTION');
        }

        $warehouseId = (int) ($data['outputWarehouseId'] ?? $order['warehouseId']);
        $warehouse = $this->warehouse($warehouseId);
        if ((int) $warehouse['isQuarantine'] === 1) {
            throw ApiException::conflict('Finished goods cannot be stored in the quarantine warehouse.',
                'PRODUCTION_QUARANTINE');
        }

        $batchNumber = trim((string) ($data['batchNumber'] ?? ''));
        if ($batchNumber === '') {
            $batchNumber = 'PF-' . (string) $order['orderNumber'];
        }
        $productionDate = $this->date($data['productionDate'] ?? null)
            ?? (new \DateTimeImmutable('today'))->format('Y-m-d');
        $expiryDate = $this->date($data['expiryDate'] ?? null);

        $materialCost = round((float) $this->db->scalar(
            'SELECT COALESCE(SUM(m.consumedQuantity * b.purchasePrice), 0)
             FROM productionMaterials m JOIN materialBatches b ON b.id = m.materialBatchId WHERE m.orderId = :id',
            ['id' => $id]
        ), 2);
        $unitCost = round(($materialCost + $packaging + $other) / $actual, 4);

        $this->db->transaction(function () use ($id, $actual, $waste, $packaging, $other, $materialCost, $unitCost,
            $warehouseId, $batchNumber, $productionDate, $expiryDate, $order, $userId): void {
            $this->db->execute(
                'INSERT INTO productBatches (productId, batchNumber, warehouseId, productionOrderId, quantity,
                        unit, productionDate, expiryDate, qualityStatus, unitCost)
                 VALUES (:productId, :batchNumber, :warehouseId, :orderId, :quantity, :unit, :productionDate,
                        :expiryDate, \'QUARANTINE\', :unitCost)',
                [
                    'productId' => (int) $order['productId'],
                    'batchNumber' => $batchNumber,
                    'warehouseId' => $warehouseId,
                    'orderId' => $id,
                    'quantity' => $actual,
                    'unit' => (string) $order['unit'],
                    'productionDate' => $productionDate,
                    'expiryDate' => $expiryDate,
                    'unitCost' => $unitCost,
                ]
            );
            $batchId = $this->db->lastId();

            $this->db->execute(
                'INSERT INTO productionOutputs (orderId, productBatchId, quantity, unit)
                 VALUES (:orderId, :batchId, :quantity, :unit)',
                ['orderId' => $id, 'batchId' => $batchId, 'quantity' => $actual, 'unit' => (string) $order['unit']]
            );
            $this->inventory->postMovement([
                'movementType' => 'PRODUCTION_IN',
                'warehouseId' => $warehouseId,
                'targetWarehouseId' => null,
                'materialBatchId' => null,
                'productBatchId' => $batchId,
                'quantity' => $actual,
                'unitCost' => $unitCost,
                'referenceType' => 'PRODUCTION_ORDER',
                'referenceId' => $id,
                'reason' => 'Entrée production',
                'userId' => $userId,
            ]);

            $this->db->execute(
                'UPDATE productionOrders SET status = :status, actualQuantity = :actual,
                        wasteQuantity = :waste, materialCost = :materialCost, packagingCost = :packaging,
                        otherCost = :other, completedAt = NOW(), updatedBy = :userId WHERE id = :id',
                [
                    'status' => 'QUALITY_CONTROL',
                    'actual' => $actual,
                    'waste' => $waste,
                    'materialCost' => $materialCost,
                    'packaging' => $packaging,
                    'other' => $other,
                    'userId' => $userId,
                    'id' => $id,
                ]
            );
        });

        return $this->find($id);
    }

    /**
     * Records a quality check. A PASS releases every finished batch of the order and closes it; a
     * FAIL rejects them, so the stock stays out of reach instead of being sold quietly.
     *
     * @param array<string,mixed> $data
     * @return array<string,mixed>
     */
    public function quality(int $id, array $data, int $userId): array
    {
        $order = $this->order($id);
        if ($order['status'] !== 'QUALITY_CONTROL') {
            throw ApiException::conflict('A quality decision requires a completed order waiting for control.',
                'PRODUCTION_INVALID_STATE');
        }

        $type = trim((string) ($data['checkType'] ?? ''));
        if ($type === '') {
            throw ApiException::validation(['checkType' => 'validation.required']);
        }
        $result = strtoupper(trim((string) ($data['result'] ?? '')));
        if (!in_array($result, ['PASS', 'FAIL'], true)) {
            throw ApiException::validation(['result' => 'validation.allowed_values']);
        }
        $parameters = $data['parameters'] ?? null;
        if ($parameters !== null && !is_array($parameters)) {
            throw ApiException::validation(['parameters' => 'validation.format']);
        }

        $this->db->transaction(function () use ($id, $type, $result, $parameters, $userId): void {
            $this->db->execute(
                'INSERT INTO qualityChecks (orderId, checkType, parameters, result, checkedBy, checkedAt, remarks)
                 VALUES (:orderId, :checkType, :parameters, :result, :checkedBy, NOW(), :remarks)',
                [
                    'orderId' => $id,
                    'checkType' => mb_substr($type, 0, 40),
                    'parameters' => $parameters === null ? null : json_encode($parameters, JSON_UNESCAPED_UNICODE),
                    'result' => $result,
                    'checkedBy' => $userId,
                    'remarks' => mb_substr(trim((string) ($data['remarks'] ?? '')), 0, 255),
                ]
            );

            if ($result === 'PASS') {
                $this->db->execute(
                    'UPDATE productBatches SET qualityStatus = \'RELEASED\', releasedAt = NOW(), releasedBy = :userId
                     WHERE productionOrderId = :orderId AND qualityStatus = \'QUARANTINE\'',
                    ['userId' => $userId, 'orderId' => $id]
                );
                $this->db->execute(
                    'UPDATE productionOrders SET status = \'COMPLETED\', updatedBy = :userId WHERE id = :id',
                    ['userId' => $userId, 'id' => $id]
                );

                return;
            }

            $this->db->execute(
                'UPDATE productBatches SET qualityStatus = \'REJECTED\'
                 WHERE productionOrderId = :orderId AND qualityStatus = \'QUARANTINE\'',
                ['orderId' => $id]
            );
            $this->db->execute(
                'UPDATE productionOrders SET status = \'REJECTED\', updatedBy = :userId WHERE id = :id',
                ['userId' => $userId, 'id' => $id]
            );
        });

        return $this->find($id);
    }

    /** @return array<string,mixed> */
    public function cancel(int $id, string $reason, int $userId): array
    {
        $order = $this->order($id);
        // The reason matters more than the status: a run that consumed material has already changed
        // stock and can never be cancelled, whatever state it is in.
        $consumed = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(consumedQuantity), 0) FROM productionMaterials WHERE orderId = :id',
            ['id' => $id]
        );
        if ($consumed > 0.0001) {
            throw ApiException::conflict('Material has already been consumed: the order cannot be cancelled.',
                'PRODUCTION_CONSUMED');
        }
        if (in_array($order['status'], ['COMPLETED', 'REJECTED', 'CANCELLED'], true)) {
            throw ApiException::conflict('A closed order cannot be cancelled.', 'PRODUCTION_INVALID_STATE');
        }

        $this->db->transaction(function () use ($id, $reason, $userId): void {
            // Reservations are kept, never deleted: the row explains what the order had planned,
            // and the CANCELLED status is what releases the plan (nothing was consumed).
            $this->db->execute(
                'UPDATE productionOrders SET status = \'CANCELLED\', notes = :notes, updatedBy = :userId WHERE id = :id',
                ['notes' => mb_substr(trim($reason), 0, 255), 'userId' => $userId, 'id' => $id]
            );
        });

        return $this->find($id);
    }

    /**
     * Batch genealogy: the finished batch, the material batches that went into it, and the supplier
     * reception each material batch came from.
     *
     * @return array<string,mixed>
     */
    public function genealogy(int $orderId): array
    {
        $order = $this->order($orderId);
        $inputs = $this->db->select(
            'SELECT m.id, m.materialBatchId, m.plannedQuantity, m.consumedQuantity, m.unit,
                    b.batchNumber, b.qualityStatus, b.expiryDate, b.supplierId, s.name AS supplierName,
                    rm.code AS materialCode, rm.commercialName AS materialName,
                    r.id AS receiptId, r.receiptNumber, r.receivedAt, po.orderNumber AS purchaseOrderNumber
             FROM productionMaterials m
             JOIN materialBatches b ON b.id = m.materialBatchId
             JOIN rawMaterials rm ON rm.id = b.materialId
             LEFT JOIN suppliers s ON s.id = b.supplierId
             LEFT JOIN goodsReceiptItems gi ON gi.materialBatchId = b.id
             LEFT JOIN goodsReceipts r ON r.id = gi.receiptId
             LEFT JOIN purchaseOrders po ON po.id = r.orderId
             WHERE m.orderId = :id
             ORDER BY rm.code, b.batchNumber',
            ['id' => $orderId]
        );

        $outputs = $this->outputs($orderId);
        $complete = $inputs !== [] && $outputs !== [];

        return [
            'order' => [
                'id' => $order['id'],
                'orderNumber' => $order['orderNumber'],
                'productCode' => $order['productCode'],
                'productName' => $order['productName'],
                'status' => $order['status'],
                'plannedDate' => $order['plannedDate'],
                'plannedQuantity' => $order['plannedQuantity'],
                'actualQuantity' => $order['actualQuantity'],
                'wasteQuantity' => $order['wasteQuantity'],
                'unit' => $order['unit'],
                'formulationCode' => $order['formulationCode'],
                'formulationVersion' => $order['formulationVersion'],
            ],
            'inputs' => array_map(static fn (array $row): array => [
                'materialCode' => (string) $row['materialCode'],
                'materialName' => (string) $row['materialName'],
                'materialBatchId' => (int) $row['materialBatchId'],
                'batchNumber' => (string) $row['batchNumber'],
                'plannedQuantity' => round((float) $row['plannedQuantity'], 3),
                'consumedQuantity' => round((float) $row['consumedQuantity'], 3),
                'unit' => (string) $row['unit'],
                'qualityStatus' => (string) $row['qualityStatus'],
                'expiryDate' => $row['expiryDate'],
                'supplierName' => (string) ($row['supplierName'] ?? ''),
                'receiptNumber' => (string) ($row['receiptNumber'] ?? ''),
                'receivedAt' => $row['receivedAt'],
                'purchaseOrderNumber' => (string) ($row['purchaseOrderNumber'] ?? ''),
            ], $inputs),
            'outputs' => $outputs,
            'qualityChecks' => $this->checks($orderId),
            'complete' => $complete,
        ];
    }

    // ------------------------------------------------------------------ internals

    /** @return array<string,mixed> */
    private function order(int $id): array
    {
        $row = $this->db->first(
            'SELECT po.*, p.sku AS productCode, p.nameFr AS productName, w.code AS warehouseCode,
                    w.name AS warehouseName, f.code AS formulationCode, v.version AS formulationVersion,
                    v.expectedYield, v.yieldUnit
             FROM productionOrders po
             JOIN products p ON p.id = po.productId
             JOIN warehouses w ON w.id = po.warehouseId
             LEFT JOIN formulationVersions v ON v.id = po.formulationVersionId
             LEFT JOIN formulations f ON f.id = v.formulationId
             WHERE po.id = :id',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Production order');
        }

        return $this->hydrateOrder($row);
    }

    /** @param array<string,mixed> $row @return array<string,mixed> */
    private function hydrateOrder(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'orderNumber' => (string) $row['orderNumber'],
            'formulationVersionId' => (int) ($row['formulationVersionId'] ?? 0),
            'formulationCode' => (string) ($row['formulationCode'] ?? ''),
            'formulationVersion' => (int) ($row['formulationVersion'] ?? 0),
            'productId' => (int) $row['productId'],
            'productCode' => (string) $row['productCode'],
            'productName' => (string) $row['productName'],
            'warehouseId' => (int) $row['warehouseId'],
            'warehouseCode' => (string) $row['warehouseCode'],
            'warehouseName' => (string) $row['warehouseName'],
            'plannedQuantity' => round((float) $row['plannedQuantity'], 3),
            'actualQuantity' => round((float) $row['actualQuantity'], 3),
            'wasteQuantity' => round((float) $row['wasteQuantity'], 3),
            'unit' => (string) $row['unit'],
            'status' => (string) $row['status'],
            'plannedDate' => (string) $row['plannedDate'],
            'startedAt' => $row['startedAt'],
            'completedAt' => $row['completedAt'],
            'materialCost' => round((float) $row['materialCost'], 2),
            'packagingCost' => round((float) $row['packagingCost'], 2),
            'otherCost' => round((float) $row['otherCost'], 2),
            'notes' => (string) $row['notes'],
            'materialLines' => (int) ($row['materialLines'] ?? 0),
            'consumedQuantity' => round((float) ($row['consumedQuantity'] ?? 0), 3),
            'outputQuantity' => round((float) ($row['outputQuantity'] ?? 0), 3),
            'qualityCheckCount' => (int) ($row['qualityCheckCount'] ?? 0),
            'createdAt' => $row['createdAt'],
            'updatedAt' => $row['updatedAt'],
        ];
    }

    /**
     * The bill of materials scaled to the ordered quantity: the version is written for its expected
     * yield, so a 500 kg order of a 100 kg formula multiplies every line by five.
     *
     * @return array<int,array<string,mixed>>
     */
    private function plan(int $versionId, float $plannedQuantity): array
    {
        $version = $this->db->first('SELECT expectedYield FROM formulationVersions WHERE id = :id',
            ['id' => $versionId]);
        if ($version === null) {
            return [];
        }
        $yield = (float) $version['expectedYield'];
        $factor = $yield > 0 ? $plannedQuantity / $yield : 1.0;

        $rows = $this->db->select(
            'SELECT i.materialId, i.quantity, i.unit, i.tolerancePct, rm.code AS materialCode,
                    rm.commercialName AS materialName, rm.purchasePrice
             FROM formulationItems i
             JOIN rawMaterials rm ON rm.id = i.materialId
             WHERE i.versionId = :id
             ORDER BY i.sortOrder, i.id',
            ['id' => $versionId]
        );

        return array_map(static fn (array $row): array => [
            'materialId' => (int) $row['materialId'],
            'materialCode' => (string) $row['materialCode'],
            'materialName' => (string) $row['materialName'],
            'unit' => (string) $row['unit'],
            'plannedQuantity' => round((float) $row['quantity'] * $factor, 3),
            'formulaQuantity' => round((float) $row['quantity'], 4),
            'unitPrice' => round((float) $row['purchasePrice'], 2),
            'tolerancePct' => round((float) $row['tolerancePct'], 4),
        ], $rows);
    }

    /** @return array<int,array<string,mixed>> */
    private function allocations(int $orderId): array
    {
        $rows = $this->db->select(
            'SELECT m.id, m.materialBatchId, m.plannedQuantity, m.consumedQuantity, m.unit,
                    b.batchNumber, b.expiryDate, b.qualityStatus, b.purchasePrice, b.warehouseId,
                    rm.code AS materialCode, rm.commercialName AS materialName
             FROM productionMaterials m
             JOIN materialBatches b ON b.id = m.materialBatchId
             JOIN rawMaterials rm ON rm.id = b.materialId
             WHERE m.orderId = :id
             ORDER BY rm.code, b.expiryDate IS NULL, b.expiryDate, b.id',
            ['id' => $orderId]
        );

        return array_map(static function (array $row): array {
            $planned = round((float) $row['plannedQuantity'], 3);
            $consumed = round((float) $row['consumedQuantity'], 3);

            return [
                'id' => (int) $row['id'],
                'materialBatchId' => (int) $row['materialBatchId'],
                'batchNumber' => (string) $row['batchNumber'],
                'materialCode' => (string) $row['materialCode'],
                'materialName' => (string) $row['materialName'],
                'plannedQuantity' => $planned,
                'consumedQuantity' => $consumed,
                'unit' => (string) $row['unit'],
                'unitPrice' => round((float) $row['purchasePrice'], 2),
                'lineCost' => round($consumed * (float) $row['purchasePrice'], 2),
                'varianceQuantity' => round($consumed - $planned, 3),
                'expiryDate' => $row['expiryDate'],
                'qualityStatus' => (string) $row['qualityStatus'],
            ];
        }, $rows);
    }

    /** @return array<int,array<string,mixed>> */
    private function outputs(int $orderId): array
    {
        $rows = $this->db->select(
            'SELECT o.id, o.productBatchId, o.quantity, o.unit, b.batchNumber, b.qualityStatus,
                    b.productionDate, b.expiryDate, b.unitCost, b.releasedAt, w.name AS warehouseName
             FROM productionOutputs o
             JOIN productBatches b ON b.id = o.productBatchId
             JOIN warehouses w ON w.id = b.warehouseId
             WHERE o.orderId = :id
             ORDER BY o.id',
            ['id' => $orderId]
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'productBatchId' => (int) $row['productBatchId'],
            'batchNumber' => (string) $row['batchNumber'],
            'quantity' => round((float) $row['quantity'], 3),
            'unit' => (string) $row['unit'],
            'qualityStatus' => (string) $row['qualityStatus'],
            'warehouseName' => (string) $row['warehouseName'],
            'productionDate' => $row['productionDate'],
            'expiryDate' => $row['expiryDate'],
            'unitCost' => round((float) $row['unitCost'], 4),
            'releasedAt' => $row['releasedAt'],
            'sellable' => (string) $row['qualityStatus'] === 'RELEASED',
        ], $rows);
    }

    /** @return array<int,array<string,mixed>> */
    private function checks(int $orderId): array
    {
        $rows = $this->db->select(
            'SELECT q.id, q.checkType, q.parameters, q.result, q.checkedAt, q.remarks, u.username AS checkedByName
             FROM qualityChecks q
             LEFT JOIN users u ON u.id = q.checkedBy
             WHERE q.orderId = :id
             ORDER BY q.id DESC',
            ['id' => $orderId]
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'checkType' => (string) $row['checkType'],
            'parameters' => $row['parameters'] === null ? null : json_decode((string) $row['parameters'], true),
            'result' => (string) $row['result'],
            'checkedAt' => $row['checkedAt'],
            'remarks' => (string) $row['remarks'],
            'checkedByName' => (string) ($row['checkedByName'] ?? ''),
        ], $rows);
    }

    /** @return array<string,mixed> */
    private function warehouse(int $id): array
    {
        $row = $this->db->first('SELECT id, code, name, isQuarantine FROM warehouses WHERE id = :id',
            ['id' => $id]);
        if ($row === null) {
            throw ApiException::notFound('Warehouse');
        }

        return $row;
    }

    private function nextNumber(): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM productionOrders') + 1;
        do {
            $number = sprintf('PRD-%05d', $sequence);
            $exists = $this->db->first('SELECT id FROM productionOrders WHERE orderNumber = :number',
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
