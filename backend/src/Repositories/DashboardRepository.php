<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\Database;

/**
 * Every figure of the executive dashboard is aggregated from the ledger tables - nothing is
 * hardcoded and nothing is stored twice.
 */
final class DashboardRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    public function summary(string $from, string $to): array
    {
        $range = ['from' => $from . ' 00:00:00', 'to' => $to . ' 23:59:59'];

        return [
            'period' => ['from' => $from, 'to' => $to],
            'currency' => (string) ($this->db->scalar('SELECT currency FROM companies ORDER BY id LIMIT 1') ?? 'DZD'),
            'revenue' => $this->revenue($range),
            'receivables' => $this->receivables(),
            'expenses' => $this->expenses($range),
            'stock' => $this->stock(),
            'production' => $this->production(),
            'partners' => $this->partners(),
            'topProducts' => $this->topProducts($range),
            'alerts' => $this->alerts(),
        ];
    }

    private function revenue(array $range): array
    {
        $total = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(totalTtc), 0) FROM invoices
             WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN :from AND :to',
            ['from' => substr($range['from'], 0, 10), 'to' => substr($range['to'], 0, 10)]
        );
        $count = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM invoices WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN :from AND :to',
            ['from' => substr($range['from'], 0, 10), 'to' => substr($range['to'], 0, 10)]
        );
        $paid = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(paidAmount), 0) FROM invoices
             WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN :from AND :to',
            ['from' => substr($range['from'], 0, 10), 'to' => substr($range['to'], 0, 10)]
        );

        return [
            'totalTtc' => round($total, 2),
            'paidTtc' => round($paid, 2),
            'invoiceCount' => $count,
            'averageBasket' => $count > 0 ? round($total / $count, 2) : 0.0,
        ];
    }

    private function receivables(): array
    {
        $row = $this->db->first(
            'SELECT COALESCE(SUM(totalTtc - paidAmount), 0) AS outstanding,
                    COALESCE(SUM(CASE WHEN dueDate < CURDATE() THEN totalTtc - paidAmount ELSE 0 END), 0) AS overdue,
                    COUNT(*) AS openInvoices
             FROM invoices WHERE status IN (\'ISSUED\', \'PARTIAL\')'
        ) ?? ['outstanding' => 0, 'overdue' => 0, 'openInvoices' => 0];

        return [
            'outstanding' => round((float) $row['outstanding'], 2),
            'overdue' => round((float) $row['overdue'], 2),
            'openInvoices' => (int) $row['openInvoices'],
        ];
    }

    private function expenses(array $range): array
    {
        $row = $this->db->first(
            'SELECT COALESCE(SUM(amount), 0) AS total, COUNT(*) AS documents FROM expenses
             WHERE status IN (\'APPROVED\', \'PAID\') AND expenseDate BETWEEN :from AND :to',
            ['from' => substr($range['from'], 0, 10), 'to' => substr($range['to'], 0, 10)]
        ) ?? ['total' => 0, 'documents' => 0];

        return ['total' => round((float) $row['total'], 2), 'documents' => (int) $row['documents']];
    }

    private function stock(): array
    {
        $materials = $this->db->first(
            'SELECT COALESCE(SUM(quantity * purchasePrice), 0) AS value, COUNT(*) AS batches
             FROM materialBatches WHERE qualityStatus = \'RELEASED\''
        ) ?? ['value' => 0, 'batches' => 0];
        $products = $this->db->first(
            'SELECT COALESCE(SUM(quantity * unitCost), 0) AS value, COUNT(*) AS batches
             FROM productBatches WHERE qualityStatus = \'RELEASED\''
        ) ?? ['value' => 0, 'batches' => 0];
        $lowStock = (int) $this->db->scalar('SELECT COUNT(*) FROM vwLowStock');
        $expiring = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM materialBatches
             WHERE qualityStatus = \'RELEASED\' AND expiryDate IS NOT NULL
               AND expiryDate BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 60 DAY)'
        );

        return [
            'materialsValue' => round((float) $materials['value'], 2),
            'productsValue' => round((float) $products['value'], 2),
            'totalValue' => round((float) $materials['value'] + (float) $products['value'], 2),
            'materialBatches' => (int) $materials['batches'],
            'productBatches' => (int) $products['batches'],
            'lowStockItems' => $lowStock,
            'expiringSoon' => $expiring,
        ];
    }

    private function production(): array
    {
        $row = $this->db->first(
            'SELECT SUM(status IN (\'DRAFT\',\'WAITING_MATERIALS\',\'APPROVED\')) AS planned,
                    SUM(status = \'IN_PROGRESS\') AS running,
                    SUM(status = \'QUALITY_CONTROL\') AS awaitingQuality,
                    SUM(status = \'COMPLETED\') AS completed,
                    COALESCE(SUM(CASE WHEN status IN (\'DRAFT\',\'WAITING_MATERIALS\',\'APPROVED\') THEN plannedQuantity ELSE 0 END), 0) AS plannedQuantity
             FROM productionOrders'
        ) ?? [];

        return [
            'planned' => (int) ($row['planned'] ?? 0),
            'inProgress' => (int) ($row['running'] ?? 0),
            'awaitingQualityControl' => (int) ($row['awaitingQuality'] ?? 0),
            'completed' => (int) ($row['completed'] ?? 0),
            'plannedQuantity' => round((float) ($row['plannedQuantity'] ?? 0), 3),
        ];
    }

    private function partners(): array
    {
        return [
            'activeProducts' => (int) $this->db->scalar('SELECT COUNT(*) FROM products WHERE deletedAt IS NULL AND isActive = 1'),
            'activeCustomers' => (int) $this->db->scalar('SELECT COUNT(*) FROM customers WHERE deletedAt IS NULL AND isActive = 1'),
            'activeSuppliers' => (int) $this->db->scalar('SELECT COUNT(*) FROM suppliers WHERE deletedAt IS NULL AND isActive = 1'),
            'activeUsers' => (int) $this->db->scalar('SELECT COUNT(*) FROM users WHERE deletedAt IS NULL AND isActive = 1'),
        ];
    }

    private function topProducts(array $range): array
    {
        $rows = $this->db->select(
            'SELECT p.id, p.sku, p.nameFr, SUM(ii.quantity) AS quantity, SUM(ii.lineTotal) AS revenue
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId AND i.status <> \'CANCELLED\'
             JOIN products p ON p.id = ii.productId
             WHERE i.invoiceDate BETWEEN :from AND :to
             GROUP BY p.id, p.sku, p.nameFr
             ORDER BY revenue DESC
             LIMIT 5',
            ['from' => substr($range['from'], 0, 10), 'to' => substr($range['to'], 0, 10)]
        );

        return array_map(static fn (array $row): array => [
            'productId' => (int) $row['id'],
            'sku' => (string) $row['sku'],
            'nameFr' => (string) $row['nameFr'],
            'quantity' => round((float) $row['quantity'], 3),
            'revenue' => round((float) $row['revenue'], 2),
        ], $rows);
    }

    /** Actionable signals: each one links to the screen that fixes it. */
    private function alerts(): array
    {
        $lowStock = $this->db->select(
            'SELECT itemKind, itemId, itemCode, itemName, quantity, minStock FROM vwLowStock
             ORDER BY (minStock - quantity) DESC LIMIT 5'
        );
        $expiring = $this->db->select(
            'SELECT b.id, b.batchNumber, m.commercialName, b.quantity, b.unit, b.expiryDate
             FROM materialBatches b JOIN rawMaterials m ON m.id = b.materialId
             WHERE b.qualityStatus = \'RELEASED\' AND b.expiryDate IS NOT NULL
               AND b.expiryDate <= DATE_ADD(CURDATE(), INTERVAL 60 DAY)
             ORDER BY b.expiryDate ASC LIMIT 5'
        );
        $overdue = $this->db->select(
            'SELECT i.id, i.invoiceNumber, i.dueDate, (i.totalTtc - i.paidAmount) AS outstanding,
                    c.name AS customerName
             FROM invoices i JOIN customers c ON c.id = i.customerId
             WHERE i.status IN (\'ISSUED\', \'PARTIAL\') AND i.dueDate < CURDATE()
             ORDER BY i.dueDate ASC LIMIT 5'
        );

        return [
            'lowStock' => $lowStock,
            'expiringBatches' => $expiring,
            'overdueInvoices' => $overdue,
        ];
    }
}
