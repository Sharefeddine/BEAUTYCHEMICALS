<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Formulation families, their versions and the bill of materials of each version.
 *
 * <p>Hard rules, enforced here and not only in the interface:</p>
 * <ul>
 *   <li>the application never invents a formula: a version exists only because a qualified user
 *       entered its items, and the API stores exactly what was entered;</li>
 *   <li>an approved version is immutable — {@code isLocked} is set in the same transaction, and any
 *       later edit is refused with {@code 409}; changes go through a new version;</li>
 *   <li>approving requires at least one item, because an empty bill of materials cannot be
 *       produced.</li>
 * </ul>
 */
final class FormulationRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, ?string $status, ?int $productId): array
    {
        $where = ['f.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(f.code LIKE :searchCode OR f.nameFr LIKE :searchFr OR f.nameAr LIKE :searchAr)';
            $params['searchCode'] = '%' . $search . '%';
            $params['searchFr'] = '%' . $search . '%';
            $params['searchAr'] = '%' . $search . '%';
        }
        if ($status !== null && $status !== '' && in_array(strtoupper($status), self::STATUSES, true)) {
            $where[] = 'f.status = :status';
            $params['status'] = strtoupper($status);
        }
        if ($productId !== null && $productId > 0) {
            $where[] = 'f.productId = :productId';
            $params['productId'] = $productId;
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM formulations f ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT f.id, f.code, f.nameFr, f.nameAr, f.productId, f.currentVersionId, f.status,
                    f.approvedBy, f.approvedAt, f.notes, f.createdAt, f.updatedAt,
                    p.sku AS productSku, p.nameFr AS productName,
                    u.username AS approvedByName,
                    v.version AS currentVersion,
                    v.expectedYield AS currentYield, v.yieldUnit AS currentYieldUnit,
                    (SELECT COUNT(*) FROM formulationVersions fv WHERE fv.formulationId = f.id) AS versionCount,
                    (SELECT COALESCE(SUM(fi.quantity * m.purchasePrice), 0)
                       FROM formulationItems fi
                       JOIN rawMaterials m ON m.id = fi.materialId
                      WHERE fi.versionId = f.currentVersionId) AS currentCost
             FROM formulations f
             LEFT JOIN products p ON p.id = f.productId
             LEFT JOIN users u ON u.id = f.approvedBy
             LEFT JOIN formulationVersions v ON v.id = f.currentVersionId
             ' . $clause . '
             ORDER BY f.updatedAt DESC, f.id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrateSummary'], $rows), 'total' => $total];
    }

    public function find(int $id): array
    {
        $row = $this->db->first(
            'SELECT f.*, p.sku AS productSku, p.nameFr AS productName, u.username AS approvedByName
             FROM formulations f
             LEFT JOIN products p ON p.id = f.productId
             LEFT JOIN users u ON u.id = f.approvedBy
             WHERE f.id = :id AND f.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Formulation');
        }

        $summary = $this->hydrateSummary($row);
        $versions = $this->versions($id);
        $summary['versionCount'] = count($versions);
        $summary['versions'] = $versions;
        $summary['currentVersion'] = null;
        foreach ($versions as $version) {
            if ($version['id'] === $summary['currentVersionId']) {
                $summary['currentVersion'] = $version;
            }
        }

        return $summary;
    }

    /** @return array<int,array> */
    public function versions(int $formulationId): array
    {
        $rows = $this->db->select(
            'SELECT v.*, u.username AS approvedByName,
                    (SELECT COUNT(*) FROM formulationItems i WHERE i.versionId = v.id) AS itemCount,
                    (SELECT COALESCE(SUM(i.quantity * m.purchasePrice), 0)
                       FROM formulationItems i
                       JOIN rawMaterials m ON m.id = i.materialId
                      WHERE i.versionId = v.id) AS cost
             FROM formulationVersions v
             LEFT JOIN users u ON u.id = v.approvedBy
             WHERE v.formulationId = :id
             ORDER BY v.version DESC',
            ['id' => $formulationId]
        );

        return array_map([$this, 'hydrateVersion'], $rows);
    }

    public function version(int $formulationId, int $versionId): array
    {
        $row = $this->db->first(
            'SELECT v.*, u.username AS approvedByName,
                    (SELECT COUNT(*) FROM formulationItems i WHERE i.versionId = v.id) AS itemCount,
                    (SELECT COALESCE(SUM(i.quantity * m.purchasePrice), 0)
                       FROM formulationItems i
                       JOIN rawMaterials m ON m.id = i.materialId
                      WHERE i.versionId = v.id) AS cost
             FROM formulationVersions v
             LEFT JOIN users u ON u.id = v.approvedBy
             WHERE v.id = :versionId AND v.formulationId = :formulationId',
            ['versionId' => $versionId, 'formulationId' => $formulationId]
        );
        if ($row === null) {
            throw ApiException::notFound('Formulation version');
        }

        $version = $this->hydrateVersion($row);
        $version['items'] = $this->items($versionId);
        $version['costBreakdown'] = $this->costBreakdown($version['items']);

        return $version;
    }

    /** @return array<int,array> */
    public function items(int $versionId): array
    {
        $rows = $this->db->select(
            'SELECT i.id, i.materialId, i.quantity, i.unit, i.percentage, i.phase, i.tolerancePct, i.sortOrder,
                    m.code AS materialCode, m.commercialName AS materialName, m.chemicalName,
                    m.purchasePrice, m.storageUnit, m.hazardClass, m.hazardPictograms,
                    COALESCE(vb.quantity, 0) AS stockQuantity
             FROM formulationItems i
             JOIN rawMaterials m ON m.id = i.materialId
             LEFT JOIN vwStockBalances vb ON vb.itemKind = \'MATERIAL\' AND vb.itemId = m.id
             WHERE i.versionId = :versionId
             ORDER BY i.sortOrder, i.id',
            ['versionId' => $versionId]
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'materialId' => (int) $row['materialId'],
            'materialCode' => (string) $row['materialCode'],
            'materialName' => (string) $row['materialName'],
            'chemicalName' => (string) ($row['chemicalName'] ?? ''),
            'quantity' => round((float) $row['quantity'], 4),
            'unit' => (string) $row['unit'],
            'percentage' => round((float) $row['percentage'], 4),
            'phase' => (string) $row['phase'],
            'tolerancePct' => round((float) $row['tolerancePct'], 4),
            'sortOrder' => (int) $row['sortOrder'],
            'purchasePrice' => round((float) $row['purchasePrice'], 4),
            'lineCost' => round((float) $row['quantity'] * (float) $row['purchasePrice'], 2),
            'stockQuantity' => round((float) $row['stockQuantity'], 3),
            'storageUnit' => (string) $row['storageUnit'],
            'hazardClass' => (string) ($row['hazardClass'] ?? ''),
            'hazardPictograms' => (string) ($row['hazardPictograms'] ?? ''),
        ], $rows);
    }

    public function create(array $data, ?int $actorId): array
    {
        $code = trim((string) ($data['code'] ?? ''));
        if ($code === '') {
            $code = $this->nextCode();
        }
        if ($this->db->first('SELECT id FROM formulations WHERE code = :code', ['code' => $code]) !== null) {
            throw ApiException::validation(['code' => 'validation.unique']);
        }

        $id = (int) $this->db->transaction(function () use ($data, $code, $actorId): int {
            $this->db->execute(
                'INSERT INTO formulations (code, nameFr, nameAr, productId, status, notes, createdBy, updatedBy)
                 VALUES (:code, :nameFr, :nameAr, :productId, \'DRAFT\', :notes, :createdBy, :updatedBy)',
                [
                    'code' => $code,
                    'nameFr' => (string) ($data['nameFr'] ?? ''),
                    'nameAr' => (string) ($data['nameAr'] ?? ($data['nameFr'] ?? '')),
                    'productId' => isset($data['productId']) && (int) $data['productId'] > 0 ? (int) $data['productId'] : null,
                    'notes' => (string) ($data['notes'] ?? ''),
                    'createdBy' => $actorId,
                    'updatedBy' => $actorId,
                ]
            );

            return $this->db->lastId();
        });

        return $this->find($id);
    }

    public function update(int $id, array $data, ?int $actorId): array
    {
        $current = $this->db->first('SELECT id, status FROM formulations WHERE id = :id AND deletedAt IS NULL', ['id' => $id]);
        if ($current === null) {
            throw ApiException::notFound('Formulation');
        }
        if (strtoupper((string) $current['status']) === 'ARCHIVED') {
            throw ApiException::conflict('The formulation is archived and cannot be edited.', 'FORMULATION_ARCHIVED');
        }

        $fields = [];
        $params = ['id' => $id, 'actor' => $actorId];
        foreach (['nameFr', 'nameAr', 'notes'] as $column) {
            if (array_key_exists($column, $data) && $data[$column] !== null) {
                $fields[] = $column . ' = :' . $column;
                $params[$column] = (string) $data[$column];
            }
        }
        if (array_key_exists('productId', $data)) {
            $fields[] = 'productId = :productId';
            $params['productId'] = (int) $data['productId'] > 0 ? (int) $data['productId'] : null;
        }
        if ($fields === []) {
            return $this->find($id);
        }

        $this->db->execute(
            'UPDATE formulations SET ' . implode(', ', $fields) . ', updatedBy = :actor WHERE id = :id',
            $params
        );

        return $this->find($id);
    }

    /**
     * Creates the next version, copying the content of the previous one so a change is a diff and
     * not a retyping exercise. The copy is a starting point: the user edits it before approval.
     */
    public function createVersion(int $formulationId, array $data, ?int $actorId): array
    {
        $formulation = $this->db->first(
            'SELECT id, currentVersionId, status FROM formulations WHERE id = :id AND deletedAt IS NULL',
            ['id' => $formulationId]
        );
        if ($formulation === null) {
            throw ApiException::notFound('Formulation');
        }

        $versionId = (int) $this->db->transaction(function () use ($formulationId, $formulation, $data, $actorId): int {
            $next = (int) $this->db->scalar(
                'SELECT COALESCE(MAX(version), 0) + 1 FROM formulationVersions WHERE formulationId = :id',
                ['id' => $formulationId]
            );
            $sourceId = isset($data['copyFromVersionId']) && (int) $data['copyFromVersionId'] > 0
                ? (int) $data['copyFromVersionId']
                : ($formulation['currentVersionId'] !== null ? (int) $formulation['currentVersionId'] : null);

            $source = $sourceId === null ? null : $this->db->first(
                'SELECT instructions, expectedYield, yieldUnit FROM formulationVersions
                 WHERE id = :id AND formulationId = :formulationId',
                ['id' => $sourceId, 'formulationId' => $formulationId]
            );

            $this->db->execute(
                'INSERT INTO formulationVersions
                    (formulationId, version, instructions, expectedYield, yieldUnit, createdBy)
                 VALUES (:formulationId, :version, :instructions, :expectedYield, :yieldUnit, :actor)',
                [
                    'formulationId' => $formulationId,
                    'version' => $next,
                    'instructions' => (string) ($data['instructions'] ?? ($source['instructions'] ?? '')),
                    'expectedYield' => (float) ($data['expectedYield'] ?? ($source['expectedYield'] ?? 0)),
                    'yieldUnit' => (string) ($data['yieldUnit'] ?? ($source['yieldUnit'] ?? 'L')),
                    'actor' => $actorId,
                ]
            );
            $newId = $this->db->lastId();

            if ($sourceId !== null) {
                $this->db->execute(
                    'INSERT INTO formulationItems
                        (versionId, materialId, quantity, unit, percentage, phase, tolerancePct, sortOrder)
                     SELECT :newId, materialId, quantity, unit, percentage, phase, tolerancePct, sortOrder
                     FROM formulationItems WHERE versionId = :sourceId',
                    ['newId' => $newId, 'sourceId' => $sourceId]
                );
            }

            $this->db->execute(
                'UPDATE formulations SET status = \'DRAFT\', updatedBy = :actor WHERE id = :id',
                ['actor' => $actorId, 'id' => $formulationId]
            );

            return $newId;
        });

        return $this->version($formulationId, $versionId);
    }

    /**
     * Replaces the content of an unlocked version. The items are sent as a whole list: replacing
     * them atomically is the only way to guarantee that the percentages and the total always
     * describe the same bill of materials.
     */
    public function updateVersion(int $formulationId, int $versionId, array $data, ?int $actorId): array
    {
        $version = $this->db->first(
            'SELECT id, isLocked FROM formulationVersions WHERE id = :id AND formulationId = :formulationId',
            ['id' => $versionId, 'formulationId' => $formulationId]
        );
        if ($version === null) {
            throw ApiException::notFound('Formulation version');
        }
        if ((int) $version['isLocked'] === 1) {
            throw ApiException::conflict(
                'This version is approved and read only. Create a new version to change it.',
                'FORMULATION_LOCKED'
            );
        }

        $items = $data['items'] ?? null;
        if ($items !== null && !is_array($items)) {
            throw ApiException::validation(['items' => 'validation.array']);
        }

        $this->db->transaction(function () use ($formulationId, $versionId, $data, $items, $actorId): void {
            $fields = [];
            $params = ['id' => $versionId];
            if (array_key_exists('instructions', $data)) {
                $fields[] = 'instructions = :instructions';
                $params['instructions'] = (string) $data['instructions'];
            }
            if (array_key_exists('expectedYield', $data)) {
                $fields[] = 'expectedYield = :expectedYield';
                $params['expectedYield'] = (float) $data['expectedYield'];
            }
            if (array_key_exists('yieldUnit', $data)) {
                $fields[] = 'yieldUnit = :yieldUnit';
                $params['yieldUnit'] = (string) $data['yieldUnit'];
            }
            if ($fields !== []) {
                $this->db->execute('UPDATE formulationVersions SET ' . implode(', ', $fields) . ' WHERE id = :id', $params);
            }

            if ($items !== null) {
                $this->replaceItems($versionId, $items);
            }

            $this->db->execute(
                'UPDATE formulations SET updatedBy = :actor WHERE id = :id',
                ['actor' => $actorId, 'id' => $formulationId]
            );
        });

        return $this->version($formulationId, $versionId);
    }

    /**
     * Approves a version: it becomes the reference of the formulation and the rows are locked.
     * The approval writes {@code isLocked}, {@code lockedAt}, the approver and the formulation
     * status in one transaction, so a half-approved formula can never exist.
     */
    public function approve(int $formulationId, int $versionId, int $actorId): array
    {
        $this->db->transaction(function () use ($formulationId, $versionId, $actorId): void {
            $version = $this->db->first(
                'SELECT id, isLocked, (SELECT COUNT(*) FROM formulationItems WHERE versionId = :countId) AS itemCount
                 FROM formulationVersions WHERE id = :id AND formulationId = :formulationId',
                ['id' => $versionId, 'formulationId' => $formulationId, 'countId' => $versionId]
            );
            if ($version === null) {
                throw ApiException::notFound('Formulation version');
            }
            if ((int) $version['isLocked'] === 1) {
                throw ApiException::conflict('This version is already approved.', 'FORMULATION_LOCKED');
            }
            if ((int) $version['itemCount'] === 0) {
                throw ApiException::validation(
                    ['items' => 'validation.required'],
                    'A formulation cannot be approved without at least one raw material.'
                );
            }

            $this->db->execute(
                'UPDATE formulationVersions
                    SET isLocked = 1, lockedAt = NOW(), approvedBy = :actor, approvedAt = NOW()
                  WHERE id = :id',
                ['actor' => $actorId, 'id' => $versionId]
            );

            $this->db->execute(
                'UPDATE formulations
                    SET status = \'APPROVED\', currentVersionId = :versionId, approvedBy = :approvedBy,
                        approvedAt = NOW(), updatedBy = :updatedBy
                  WHERE id = :id AND deletedAt IS NULL',
                ['versionId' => $versionId, 'approvedBy' => $actorId, 'updatedBy' => $actorId,
                    'id' => $formulationId]
            );

            $this->db->execute(
                'UPDATE products p JOIN formulations f ON f.productId = p.id
                    SET p.formulationId = f.id
                  WHERE f.id = :id AND p.formulationId IS NULL',
                ['id' => $formulationId]
            );
        });

        return $this->version($formulationId, $versionId);
    }

    /** Archives a formulation without touching its versions (they stay readable forever). */
    public function archive(int $id, ?int $actorId): array
    {
        $updated = $this->db->execute(
            'UPDATE formulations SET status = \'ARCHIVED\', updatedBy = :actor WHERE id = :id AND deletedAt IS NULL',
            ['actor' => $actorId, 'id' => $id]
        );
        if ($updated === 0) {
            throw ApiException::notFound('Formulation');
        }

        return $this->find($id);
    }

    /**
     * Compares two versions of the same formulation: the item level diff and the cost impact at the
     * current purchase prices.
     */
    public function compare(int $formulationId, int $leftId, int $rightId): array
    {
        $left = $this->version($formulationId, $leftId);
        $right = $this->version($formulationId, $rightId);

        $index = static function (array $items): array {
            $map = [];
            foreach ($items as $item) {
                $map[(int) $item['materialId']] = $item;
            }

            return $map;
        };
        $leftItems = $index($left['items']);
        $rightItems = $index($right['items']);

        $rows = [];
        foreach ($leftItems as $materialId => $item) {
            $other = $rightItems[$materialId] ?? null;
            $rows[] = [
                'materialId' => $materialId,
                'materialCode' => $item['materialCode'],
                'materialName' => $item['materialName'],
                'unit' => $item['unit'],
                'leftQuantity' => $item['quantity'],
                'rightQuantity' => $other['quantity'] ?? 0.0,
                'delta' => round(($other['quantity'] ?? 0.0) - $item['quantity'], 4),
                'status' => $other === null ? 'REMOVED' : (abs($other['quantity'] - $item['quantity']) > 0.00005 ? 'CHANGED' : 'UNCHANGED'),
            ];
        }
        foreach ($rightItems as $materialId => $item) {
            if (!isset($leftItems[$materialId])) {
                $rows[] = [
                    'materialId' => $materialId,
                    'materialCode' => $item['materialCode'],
                    'materialName' => $item['materialName'],
                    'unit' => $item['unit'],
                    'leftQuantity' => 0.0,
                    'rightQuantity' => $item['quantity'],
                    'delta' => $item['quantity'],
                    'status' => 'ADDED',
                ];
            }
        }

        return [
            'left' => ['id' => $left['id'], 'version' => $left['version'], 'cost' => $left['cost'],
                'itemCount' => $left['itemCount'], 'isLocked' => $left['isLocked']],
            'right' => ['id' => $right['id'], 'version' => $right['version'], 'cost' => $right['cost'],
                'itemCount' => $right['itemCount'], 'isLocked' => $right['isLocked']],
            'costDelta' => round($right['cost'] - $left['cost'], 2),
            'items' => $rows,
        ];
    }

    // ------------------------------------------------------------------ internals

    /** @param array<int,array> $items */
    private function replaceItems(int $versionId, array $items): void
    {
        $clean = [];
        foreach ($items as $item) {
            if (!is_array($item)) {
                continue;
            }
            $quantity = (float) ($item['quantity'] ?? 0);
            if ($quantity <= 0) {
                throw ApiException::validation(['items' => 'validation.positive']);
            }
            $materialId = (int) ($item['materialId'] ?? 0);
            if ($materialId <= 0) {
                throw ApiException::validation(['items' => 'validation.required']);
            }
            $clean[] = [
                'materialId' => $materialId,
                'quantity' => $quantity,
                'unit' => (string) ($item['unit'] ?? 'KG'),
                'phase' => (string) ($item['phase'] ?? ''),
                'tolerancePct' => (float) ($item['tolerancePct'] ?? 0),
                'sortOrder' => (int) ($item['sortOrder'] ?? count($clean) + 1),
            ];
        }

        $known = [];
        foreach ($this->db->select('SELECT id FROM rawMaterials WHERE deletedAt IS NULL') as $row) {
            $known[(int) $row['id']] = true;
        }
        foreach ($clean as $item) {
            if (!isset($known[$item['materialId']])) {
                throw ApiException::validation(['items' => 'validation.exists']);
            }
        }

        $this->db->execute('DELETE FROM formulationItems WHERE versionId = :versionId', ['versionId' => $versionId]);

        // percentages are only meaningful when every line shares the same unit
        $units = array_values(array_unique(array_map(static fn (array $item): string => $item['unit'], $clean)));
        $total = array_sum(array_map(static fn (array $item): float => $item['quantity'], $clean));
        $percentages = $units !== [] && count($units) === 1 && $total > 0;

        foreach ($clean as $item) {
            $this->db->execute(
                'INSERT INTO formulationItems
                    (versionId, materialId, quantity, unit, percentage, phase, tolerancePct, sortOrder)
                 VALUES (:versionId, :materialId, :quantity, :unit, :percentage, :phase, :tolerancePct, :sortOrder)',
                [
                    'versionId' => $versionId,
                    'materialId' => $item['materialId'],
                    'quantity' => $item['quantity'],
                    'unit' => $item['unit'],
                    'percentage' => $percentages ? round($item['quantity'] / $total * 100, 4) : 0,
                    'phase' => $item['phase'],
                    'tolerancePct' => $item['tolerancePct'],
                    'sortOrder' => $item['sortOrder'],
                ]
            );
        }
    }

    /** @param array<int,array> $items */
    private function costBreakdown(array $items): array
    {
        $total = 0.0;
        foreach ($items as $item) {
            $total += (float) $item['lineCost'];
        }

        return ['total' => round($total, 2), 'currency' => 'DZD', 'priceSource' => 'currentPurchasePrice'];
    }

    private function nextCode(): string
    {
        $sequence = (int) $this->db->scalar('SELECT COUNT(*) FROM formulations') + 1;
        do {
            $code = sprintf('FML-%04d', $sequence);
            $exists = $this->db->first('SELECT id FROM formulations WHERE code = :code', ['code' => $code]) !== null;
            $sequence++;
        } while ($exists);

        return $code;
    }

    private function hydrateSummary(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'nameFr' => (string) $row['nameFr'],
            'nameAr' => (string) $row['nameAr'],
            'productId' => isset($row['productId']) ? (int) $row['productId'] : null,
            'productSku' => $row['productSku'] ?? null,
            'productName' => $row['productName'] ?? null,
            'currentVersionId' => isset($row['currentVersionId']) ? (int) $row['currentVersionId'] : null,
            'currentVersion' => isset($row['currentVersion']) ? (int) $row['currentVersion'] : null,
            'currentYield' => isset($row['currentYield']) ? round((float) $row['currentYield'], 3) : null,
            'currentYieldUnit' => $row['currentYieldUnit'] ?? null,
            'currentCost' => isset($row['currentCost']) ? round((float) $row['currentCost'], 2) : 0.0,
            'status' => (string) $row['status'],
            'approvedBy' => isset($row['approvedBy']) ? (int) $row['approvedBy'] : null,
            'approvedByName' => $row['approvedByName'] ?? null,
            'approvedAt' => $row['approvedAt'] ?? null,
            'notes' => (string) ($row['notes'] ?? ''),
            'createdAt' => $row['createdAt'] ?? null,
            'updatedAt' => $row['updatedAt'] ?? null,
        ];
    }

    private function hydrateVersion(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'formulationId' => (int) $row['formulationId'],
            'version' => (int) $row['version'],
            'instructions' => (string) ($row['instructions'] ?? ''),
            'expectedYield' => round((float) $row['expectedYield'], 3),
            'yieldUnit' => (string) $row['yieldUnit'],
            'isLocked' => (int) $row['isLocked'] === 1,
            'lockedAt' => $row['lockedAt'] ?? null,
            'approvedBy' => isset($row['approvedBy']) ? (int) $row['approvedBy'] : null,
            'approvedByName' => $row['approvedByName'] ?? null,
            'approvedAt' => $row['approvedAt'] ?? null,
            'createdAt' => $row['createdAt'] ?? null,
            'updatedAt' => $row['updatedAt'] ?? null,
            'itemCount' => (int) ($row['itemCount'] ?? 0),
            'cost' => round((float) ($row['cost'] ?? 0), 2),
        ];
    }

    private const STATUSES = ['DRAFT', 'PENDING', 'APPROVED', 'ARCHIVED'];
}
