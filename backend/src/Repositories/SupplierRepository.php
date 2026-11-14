<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Suppliers and their purchasing history.
 */
final class SupplierRepository
{
    private const SORTABLE = ['code', 'name', 'wilaya', 'balance', 'createdAt'];

    public function __construct(private readonly Database $db)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, bool $activeOnly, string $sort,
                             string $direction): array
    {
        $sort = in_array($sort, self::SORTABLE, true) ? $sort : 'name';
        $direction = strtoupper($direction) === 'DESC' ? 'DESC' : 'ASC';
        $where = ['s.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(s.code LIKE :searchCode OR s.name LIKE :searchName OR s.wilaya LIKE :searchWilaya
                         OR s.contactName LIKE :searchContact OR s.phone LIKE :searchPhone)';
            foreach (['searchCode', 'searchName', 'searchWilaya', 'searchContact', 'searchPhone'] as $key) {
                $params[$key] = '%' . $search . '%';
            }
        }
        if ($activeOnly) {
            $where[] = 's.isActive = 1';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM suppliers s ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT s.id, s.code, s.name, s.contactName, s.phone, s.email, s.address, s.wilaya,
                    s.paymentTerms, s.balance, s.notes, s.isActive, s.createdAt,
                    (SELECT COUNT(*) FROM purchaseOrders po WHERE po.supplierId = s.id) AS orderCount,
                    (SELECT MAX(po.orderDate) FROM purchaseOrders po WHERE po.supplierId = s.id) AS lastOrderDate,
                    (SELECT COUNT(*) FROM rawMaterials m WHERE m.supplierId = s.id AND m.deletedAt IS NULL)
                        AS materialCount
             FROM suppliers s ' . $clause . '
             ORDER BY s.' . $sort . ' ' . $direction . '
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrate'], $rows), 'total' => $total];
    }

    /** @return array<string,mixed> */
    public function find(int $id): array
    {
        $row = $this->db->first(
            'SELECT s.*,
                    (SELECT COUNT(*) FROM purchaseOrders po WHERE po.supplierId = s.id) AS orderCount,
                    (SELECT MAX(po.orderDate) FROM purchaseOrders po WHERE po.supplierId = s.id) AS lastOrderDate
             FROM suppliers s WHERE s.id = :id AND s.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('Supplier');
        }
        $supplier = $this->hydrate($row);
        $supplier['materials'] = $this->db->select(
            'SELECT id, code, commercialName, purchasePrice, storageUnit FROM rawMaterials
             WHERE supplierId = :id AND deletedAt IS NULL ORDER BY commercialName',
            ['id' => $id]
        );
        $supplier['recentOrders'] = $this->db->select(
            'SELECT id, orderNumber, orderDate, status, totalTtc FROM purchaseOrders
             WHERE supplierId = :id ORDER BY orderDate DESC, id DESC LIMIT 10',
            ['id' => $id]
        );

        return $supplier;
    }

    private function hydrate(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'code' => (string) $row['code'],
            'name' => (string) $row['name'],
            'contactName' => (string) ($row['contactName'] ?? ''),
            'phone' => (string) ($row['phone'] ?? ''),
            'email' => (string) ($row['email'] ?? ''),
            'address' => (string) ($row['address'] ?? ''),
            'wilaya' => (string) ($row['wilaya'] ?? ''),
            'paymentTerms' => (string) ($row['paymentTerms'] ?? ''),
            'balance' => round((float) ($row['balance'] ?? 0), 2),
            'notes' => $row['notes'] ?? null,
            'isActive' => (int) ($row['isActive'] ?? 1) === 1,
            'orderCount' => (int) ($row['orderCount'] ?? 0),
            'lastOrderDate' => $row['lastOrderDate'] ?? null,
            'materialCount' => (int) ($row['materialCount'] ?? 0),
            'createdAt' => $row['createdAt'] ?? null,
        ];
    }
}
