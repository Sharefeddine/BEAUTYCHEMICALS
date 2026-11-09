<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Finished products. Stock is never read from this table: it comes from the batches (vwStockBalances).
 */
final class ProductRepository
{
    private const SORTABLE = ['sku', 'nameFr', 'nameAr', 'wholesalePrice', 'retailPrice', 'minStock', 'createdAt'];

    public function __construct(private readonly Database $db)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, ?int $categoryId, string $type,
                             string $status, string $sort, string $direction): array
    {
        $sort = in_array($sort, self::SORTABLE, true) ? $sort : 'nameFr';
        $direction = strtoupper($direction) === 'DESC' ? 'DESC' : 'ASC';
        $where = ['p.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(p.sku LIKE :search OR p.nameFr LIKE :search OR p.nameAr LIKE :search OR p.barcode LIKE :search)';
            $params['search'] = '%' . $search . '%';
        }
        if ($categoryId !== null && $categoryId > 0) {
            $where[] = 'p.categoryId = :categoryId';
            $params['categoryId'] = $categoryId;
        }
        if ($type !== '') {
            $where[] = 'p.type = :type';
            $params['type'] = strtoupper($type);
        }
        if ($status === 'active') {
            $where[] = 'p.isActive = 1';
        } elseif ($status === 'inactive') {
            $where[] = 'p.isActive = 0';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM products p ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT p.id, p.sku, p.barcode, p.nameFr, p.nameAr, p.description, p.categoryId, c.nameFr AS categoryName,
                    p.type, p.unit, p.packaging, p.unitsPerCarton, p.minStock, p.maxStock, p.wholesalePrice,
                    p.retailPrice, p.specialPrice, p.purchaseCost, p.vatRate, p.isActive, p.createdAt,
                    COALESCE(s.quantity, 0) AS stockQuantity
             FROM products p
             LEFT JOIN productCategories c ON c.id = p.categoryId
             LEFT JOIN vwStockBalances s ON s.itemKind = \'PRODUCT\' AND s.itemId = p.id
             ' . $clause . '
             ORDER BY p.' . $sort . ' ' . $direction . '
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrate'], $rows), 'total' => $total];
    }

    public function find(int $id): array
    {
        $row = $this->db->first(
            'SELECT p.*, c.nameFr AS categoryName, COALESCE(s.quantity, 0) AS stockQuantity
             FROM products p
             LEFT JOIN productCategories c ON c.id = p.categoryId
             LEFT JOIN vwStockBalances s ON s.itemKind = \'PRODUCT\' AND s.itemId = p.id
             WHERE p.id = :id AND p.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Product');
        }

        return $this->hydrate($row);
    }

    public function create(array $data, ?int $actorId): array
    {
        $this->db->execute(
            'INSERT INTO products (sku, barcode, nameFr, nameAr, description, categoryId, type, unit, packaging,
                                   packagingCapacity, capacityUnit, unitsPerCarton, minStock, maxStock,
                                   purchaseCost, productionCost, wholesalePrice, retailPrice, specialPrice,
                                   vatRate, isActive, createdBy)
             VALUES (:sku, :barcode, :nameFr, :nameAr, :description, :categoryId, :type, :unit, :packaging,
                     :packagingCapacity, :capacityUnit, :unitsPerCarton, :minStock, :maxStock,
                     :purchaseCost, :productionCost, :wholesalePrice, :retailPrice, :specialPrice,
                     :vatRate, :isActive, :createdBy)',
            [
                'sku' => $data['sku'],
                'barcode' => $data['barcode'] ?? null,
                'nameFr' => $data['nameFr'],
                'nameAr' => $data['nameAr'],
                'description' => $data['description'] ?? '',
                'categoryId' => (int) $data['categoryId'],
                'type' => strtoupper($data['type'] ?? 'MANUFACTURED'),
                'unit' => $data['unit'] ?? 'PCE',
                'packaging' => $data['packaging'] ?? '',
                'packagingCapacity' => (float) ($data['packagingCapacity'] ?? 0),
                'capacityUnit' => $data['capacityUnit'] ?? 'ML',
                'unitsPerCarton' => (int) ($data['unitsPerCarton'] ?? 1),
                'minStock' => (int) ($data['minStock'] ?? 0),
                'maxStock' => (int) ($data['maxStock'] ?? 0),
                'purchaseCost' => (float) ($data['purchaseCost'] ?? 0),
                'productionCost' => (float) ($data['productionCost'] ?? 0),
                'wholesalePrice' => (float) ($data['wholesalePrice'] ?? 0),
                'retailPrice' => (float) ($data['retailPrice'] ?? 0),
                'specialPrice' => (float) ($data['specialPrice'] ?? 0),
                'vatRate' => (float) ($data['vatRate'] ?? 19),
                'isActive' => (int) ($data['isActive'] ?? true),
                'createdBy' => $actorId,
            ]
        );

        return $this->find($this->db->lastId());
    }

    public function update(int $id, array $data, ?int $actorId): array
    {
        $current = $this->find($id);
        $merged = array_merge($current, array_filter($data, static fn ($value): bool => $value !== null));
        $this->db->execute(
            'UPDATE products SET sku = :sku, barcode = :barcode, nameFr = :nameFr, nameAr = :nameAr,
                    description = :description, categoryId = :categoryId, type = :type, unit = :unit,
                    packaging = :packaging, unitsPerCarton = :unitsPerCarton, minStock = :minStock,
                    maxStock = :maxStock, purchaseCost = :purchaseCost, productionCost = :productionCost,
                    wholesalePrice = :wholesalePrice, retailPrice = :retailPrice, specialPrice = :specialPrice,
                    vatRate = :vatRate, isActive = :isActive, updatedBy = :updatedBy
             WHERE id = :id',
            [
                'sku' => $merged['sku'],
                'barcode' => $merged['barcode'],
                'nameFr' => $merged['nameFr'],
                'nameAr' => $merged['nameAr'],
                'description' => $merged['description'],
                'categoryId' => (int) $merged['categoryId'],
                'type' => $merged['type'],
                'unit' => $merged['unit'],
                'packaging' => $merged['packaging'],
                'unitsPerCarton' => (int) $merged['unitsPerCarton'],
                'minStock' => (int) $merged['minStock'],
                'maxStock' => (int) $merged['maxStock'],
                'purchaseCost' => (float) $merged['purchaseCost'],
                'productionCost' => (float) $merged['productionCost'],
                'wholesalePrice' => (float) $merged['wholesalePrice'],
                'retailPrice' => (float) $merged['retailPrice'],
                'specialPrice' => (float) $merged['specialPrice'],
                'vatRate' => (float) $merged['vatRate'],
                'isActive' => (int) (bool) $merged['isActive'],
                'updatedBy' => $actorId,
                'id' => $id,
            ]
        );

        return $this->find($id);
    }

    public function softDelete(int $id, ?int $actorId): void
    {
        $this->find($id);
        $this->db->execute('UPDATE products SET deletedAt = NOW(), isActive = 0, updatedBy = :actor WHERE id = :id',
            ['actor' => $actorId, 'id' => $id]);
    }

    /** @return array<int,array> */
    public function categories(): array
    {
        return $this->db->select(
            'SELECT id, code, nameFr, nameAr, nameEn, sortOrder FROM productCategories
             WHERE isActive = 1 ORDER BY sortOrder, nameFr'
        );
    }

    private function hydrate(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'sku' => (string) $row['sku'],
            'barcode' => $row['barcode'] !== null ? (string) $row['barcode'] : null,
            'nameFr' => (string) $row['nameFr'],
            'nameAr' => (string) $row['nameAr'],
            'description' => (string) ($row['description'] ?? ''),
            'categoryId' => (int) $row['categoryId'],
            'categoryName' => $row['categoryName'] ?? null,
            'type' => (string) $row['type'],
            'unit' => (string) $row['unit'],
            'packaging' => (string) ($row['packaging'] ?? ''),
            'unitsPerCarton' => (int) ($row['unitsPerCarton'] ?? 1),
            'minStock' => (int) ($row['minStock'] ?? 0),
            'maxStock' => (int) ($row['maxStock'] ?? 0),
            'purchaseCost' => (float) ($row['purchaseCost'] ?? 0),
            'productionCost' => (float) ($row['productionCost'] ?? 0),
            'wholesalePrice' => (float) ($row['wholesalePrice'] ?? 0),
            'retailPrice' => (float) ($row['retailPrice'] ?? 0),
            'specialPrice' => (float) ($row['specialPrice'] ?? 0),
            'vatRate' => (float) ($row['vatRate'] ?? 19),
            'isActive' => (int) ($row['isActive'] ?? 1) === 1,
            'stockQuantity' => (float) ($row['stockQuantity'] ?? 0),
            'createdAt' => $row['createdAt'] ?? null,
        ];
    }
}
