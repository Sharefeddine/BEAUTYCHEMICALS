<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Raw materials with their hazard data and the batches still in stock (FEFO order).
 */
final class MaterialRepository
{
    private const SORTABLE = ['code', 'commercialName', 'chemicalName', 'purchasePrice', 'minStock', 'createdAt'];

    public function __construct(private readonly Database $db)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, ?int $categoryId, string $sort,
                             string $direction, bool $hazardousOnly = false, bool $lowStockOnly = false): array
    {
        $sort = in_array($sort, self::SORTABLE, true) ? $sort : 'commercialName';
        $direction = strtoupper($direction) === 'DESC' ? 'DESC' : 'ASC';
        $where = ['m.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(m.code LIKE :searchCode OR m.commercialName LIKE :searchName OR m.chemicalName LIKE :searchChemical)';
            $params['searchCode'] = '%' . $search . '%';
            $params['searchName'] = '%' . $search . '%';
            $params['searchChemical'] = '%' . $search . '%';
        }
        if ($categoryId !== null && $categoryId > 0) {
            $where[] = 'm.categoryId = :categoryId';
            $params['categoryId'] = $categoryId;
        }
        if ($hazardousOnly) {
            $where[] = "COALESCE(m.hazardPictograms, '') <> ''";
        }
        if ($lowStockOnly) {
            // the count query has no join on the view, so the balance is expressed as a subquery
            $where[] = 'COALESCE((SELECT SUM(b.quantity) FROM materialBatches b
                                  WHERE b.materialId = m.id AND b.qualityStatus = \'RELEASED\'), 0) <= m.minStock';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM rawMaterials m ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT m.id, m.code, m.commercialName, m.chemicalName, m.categoryId, c.nameFr AS categoryName,
                    m.purchaseUnit, m.storageUnit, m.purchasePrice, m.minStock, m.storageLocation, m.hazardClass,
                    m.hazardPictograms, m.sdsAvailable, m.isActive, s.name AS supplierName,
                    COALESCE(v.quantity, 0) AS stockQuantity, v.nearestExpiry
             FROM rawMaterials m
             LEFT JOIN rawMaterialCategories c ON c.id = m.categoryId
             LEFT JOIN suppliers s ON s.id = m.supplierId
             LEFT JOIN vwStockBalances v ON v.itemKind = \'MATERIAL\' AND v.itemId = m.id
             ' . $clause . '
             ORDER BY m.' . $sort . ' ' . $direction . '
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrate'], $rows), 'total' => $total];
    }

    public function find(int $id): array
    {
        $row = $this->db->first(
            'SELECT m.*, c.nameFr AS categoryName, s.name AS supplierName, v.quantity AS stockQuantity,
                    v.nearestExpiry
             FROM rawMaterials m
             LEFT JOIN rawMaterialCategories c ON c.id = m.categoryId
             LEFT JOIN suppliers s ON s.id = m.supplierId
             LEFT JOIN vwStockBalances v ON v.itemKind = \'MATERIAL\' AND v.itemId = m.id
             WHERE m.id = :id AND m.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Raw material');
        }
        $material = $this->hydrate($row);
        $material['batches'] = $this->batches($id);

        return $material;
    }

    /** FEFO: earliest expiry first, released batches only. */
    public function batches(int $materialId): array
    {
        $rows = $this->db->select(
            'SELECT b.id, b.batchNumber, b.quantity, b.unit, b.productionDate, b.expiryDate, b.qualityStatus,
                    b.purchasePrice, w.name AS warehouseName
             FROM materialBatches b
             JOIN warehouses w ON w.id = b.warehouseId
             WHERE b.materialId = :id
             ORDER BY b.expiryDate IS NULL, b.expiryDate ASC',
            ['id' => $materialId]
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'batchNumber' => (string) $row['batchNumber'],
            'quantity' => (float) $row['quantity'],
            'unit' => (string) $row['unit'],
            'productionDate' => $row['productionDate'],
            'expiryDate' => $row['expiryDate'],
            'qualityStatus' => (string) $row['qualityStatus'],
            'purchasePrice' => (float) $row['purchasePrice'],
            'warehouseName' => (string) $row['warehouseName'],
        ], $rows);
    }

    /** @return array<int,array> */
    public function categories(): array
    {
        return $this->db->select(
            'SELECT id, code, nameFr, nameAr, nameEn, sortOrder FROM rawMaterialCategories
             WHERE isActive = 1 ORDER BY sortOrder, nameFr'
        );
    }

    public function update(int $id, array $data, ?int $actorId): array
    {
        $current = $this->find($id);
        $this->db->execute(
            'UPDATE rawMaterials SET commercialName = :commercialName, chemicalName = :chemicalName,
                    categoryId = :categoryId, supplierId = :supplierId, purchaseUnit = :purchaseUnit,
                    storageUnit = :storageUnit, conversionFactor = :conversionFactor,
                    purchasePrice = :purchasePrice, minStock = :minStock, storageLocation = :storageLocation,
                    hazardClass = :hazardClass, hazardPictograms = :hazardPictograms,
                    storageConditions = :storageConditions, isActive = :isActive, updatedBy = :updatedBy
             WHERE id = :id',
            [
                'commercialName' => $data['commercialName'] ?? $current['commercialName'],
                'chemicalName' => $data['chemicalName'] ?? $current['chemicalName'],
                'categoryId' => (int) ($data['categoryId'] ?? $current['categoryId']),
                'supplierId' => $data['supplierId'] ?? null,
                'purchaseUnit' => $data['purchaseUnit'] ?? $current['purchaseUnit'],
                'storageUnit' => $data['storageUnit'] ?? $current['storageUnit'],
                'conversionFactor' => (float) ($data['conversionFactor'] ?? 1),
                'purchasePrice' => (float) ($data['purchasePrice'] ?? $current['purchasePrice']),
                'minStock' => (float) ($data['minStock'] ?? $current['minStock']),
                'storageLocation' => $data['storageLocation'] ?? '',
                'hazardClass' => $data['hazardClass'] ?? '',
                'hazardPictograms' => $data['hazardPictograms'] ?? '',
                'storageConditions' => $data['storageConditions'] ?? '',
                'isActive' => (int) ($data['isActive'] ?? $current['isActive']),
                'updatedBy' => $actorId,
                'id' => $id,
            ]
        );

        return $this->find($id);
    }

    private function hydrate(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'commercialName' => (string) $row['commercialName'],
            'chemicalName' => (string) ($row['chemicalName'] ?? ''),
            'categoryId' => (int) $row['categoryId'],
            'categoryName' => $row['categoryName'] ?? null,
            'supplierName' => $row['supplierName'] ?? null,
            'purchaseUnit' => (string) ($row['purchaseUnit'] ?? 'KG'),
            'storageUnit' => (string) ($row['storageUnit'] ?? 'KG'),
            'conversionFactor' => (float) ($row['conversionFactor'] ?? 1),
            'purchasePrice' => (float) ($row['purchasePrice'] ?? 0),
            'minStock' => (float) ($row['minStock'] ?? 0),
            'storageLocation' => (string) ($row['storageLocation'] ?? ''),
            'hazardClass' => (string) ($row['hazardClass'] ?? ''),
            'hazardPictograms' => (string) ($row['hazardPictograms'] ?? ''),
            'storageConditions' => (string) ($row['storageConditions'] ?? ''),
            'sdsAvailable' => (int) ($row['sdsAvailable'] ?? 0) === 1,
            'isActive' => (int) ($row['isActive'] ?? 1) === 1,
            'stockQuantity' => (float) ($row['stockQuantity'] ?? 0),
            'nearestExpiry' => $row['nearestExpiry'] ?? null,
        ];
    }
}
