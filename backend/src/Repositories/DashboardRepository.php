<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\Database;

/**
 * Executive dashboard payload.
 *
 * Every figure is aggregated from the ledger tables (invoices, purchase orders, expenses, stock
 * movements, production orders, audit log) over the requested period. Nothing is hardcoded and
 * nothing is stored twice: if the database is empty the payload is honestly zero/empty.
 *
 * The JSON keys match the client records one for one (KpiSet, MonthPoint, CategoryShare,
 * TopProduct, CustomerPerformance, StockAlert, ActivityEntry), so the desktop maps them directly.
 */
final class DashboardRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    public function summary(string $from, string $to): array
    {
        $range = ['from' => $from, 'to' => $to];

        return [
            'period' => $range,
            'generatedAt' => date('c'),
            'currency' => (string) ($this->db->scalar('SELECT currency FROM companies ORDER BY id LIMIT 1') ?? 'DZD'),
            'kpis' => $this->kpis($from, $to),
            'monthly' => $this->monthly($to),
            'salesByCategory' => $this->salesByCategory($from, $to),
            'inventoryDistribution' => $this->inventoryDistribution(),
            'bestSellers' => $this->productRanking($from, $to, 'quantity'),
            'mostProfitable' => $this->productRanking($from, $to, 'profit'),
            'topCustomers' => $this->topCustomers($from, $to),
            'lowStockAlerts' => $this->lowStockAlerts(),
            'expiryAlerts' => $this->expiryAlerts(),
            'recentActivities' => $this->recentActivities(),
        ];
    }

    // ------------------------------------------------------------------ KPIs

    private function kpis(string $from, string $to): array
    {
        $range = ['from' => $from, 'to' => $to];
        $sales = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc), 0) FROM invoices
             WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN :from AND :to',
            $range
        );
        $invoices = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM invoices WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN :from AND :to',
            $range
        );
        $purchases = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc), 0) FROM purchaseOrders
             WHERE status <> \'CANCELLED\' AND orderDate BETWEEN :from AND :to',
            $range
        );
        $expenses = $this->scalarValue(
            'SELECT COALESCE(SUM(amount), 0) FROM expenses
             WHERE status IN (\'APPROVED\', \'PAID\') AND expenseDate BETWEEN :from AND :to',
            $range
        );
        $grossProfit = $this->scalarValue(
            'SELECT COALESCE(SUM(ii.lineTotal - (ii.quantity * p.productionCost)), 0)
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId AND i.status <> \'CANCELLED\'
             JOIN products p ON p.id = ii.productId
             WHERE i.invoiceDate BETWEEN :from AND :to',
            $range
        );
        $salesToday = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc), 0) FROM invoices WHERE status <> \'CANCELLED\' AND invoiceDate = CURDATE()'
        );
        $salesMonth = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc), 0) FROM invoices
             WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN DATE_FORMAT(CURDATE(), \'%Y-%m-01\') AND LAST_DAY(CURDATE())'
        );
        $salesYear = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc), 0) FROM invoices
             WHERE status <> \'CANCELLED\' AND YEAR(invoiceDate) = YEAR(CURDATE())'
        );
        $materialsValue = $this->scalarValue(
            'SELECT COALESCE(SUM(quantity * purchasePrice), 0) FROM materialBatches WHERE qualityStatus = \'RELEASED\''
        );
        $productsValue = $this->scalarValue(
            'SELECT COALESCE(SUM(quantity * unitCost), 0) FROM productBatches WHERE qualityStatus = \'RELEASED\''
        );
        $receivables = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc - paidAmount), 0) FROM invoices WHERE status IN (\'ISSUED\', \'PARTIAL\')'
        );
        $payables = $this->scalarValue(
            'SELECT COALESCE(SUM(totalTtc - paidAmount), 0) FROM supplierInvoices
             WHERE status IN (\'RECEIVED\', \'PARTIAL\')'
        );
        $opening = $this->scalarValue('SELECT COALESCE(SUM(openingBalance), 0) FROM cashAccounts WHERE isActive = 1');
        $cashIn = $this->scalarValue('SELECT COALESCE(SUM(amount), 0) FROM cashTransactions WHERE direction = \'IN\'');
        $cashOut = $this->scalarValue('SELECT COALESCE(SUM(amount), 0) FROM cashTransactions WHERE direction = \'OUT\'');
        $productionQuantity = $this->scalarValue(
            'SELECT COALESCE(SUM(actualQuantity), 0) FROM productionOrders
             WHERE status = \'COMPLETED\' AND DATE(completedAt) BETWEEN :from AND :to',
            $range
        );
        $lowStock = (int) $this->db->scalar('SELECT COUNT(*) FROM vwLowStock');
        $expiring = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM materialBatches
             WHERE expiryDate IS NOT NULL AND expiryDate <= DATE_ADD(CURDATE(), INTERVAL 60 DAY)
               AND quantity > 0'
        );
        $pendingProduction = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM productionOrders WHERE status IN (\'DRAFT\', \'WAITING_MATERIALS\', \'APPROVED\')'
        );
        $openOrders = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM salesOrders WHERE status IN (\'DRAFT\', \'CONFIRMED\', \'RESERVED\', \'PARTIAL\')'
        );

        return [
            'salesToday' => $salesToday,
            'salesMonth' => $salesMonth,
            'salesYear' => $salesYear,
            'salesInRange' => $sales,
            'purchasesInRange' => $purchases,
            'expensesInRange' => $expenses,
            'grossProfitInRange' => $grossProfit,
            'netProfitInRange' => round($grossProfit - $expenses, 2),
            'inventoryValue' => round($materialsValue + $productsValue, 2),
            'receivables' => $receivables,
            'payables' => $payables,
            'cashBalance' => round($opening + $cashIn - $cashOut, 2),
            'productionQuantityInRange' => round($productionQuantity, 3),
            'averageBasket' => $invoices > 0 ? round($sales / $invoices, 2) : 0.0,
            'lowStockCount' => $lowStock,
            'expiringCount' => $expiring,
            'pendingProductionCount' => $pendingProduction,
            'openOrders' => $openOrders,
        ];
    }

    // ------------------------------------------------------------------ series

    private function monthly(string $to): array
    {
        $end = new \DateTimeImmutable($to);
        $start = $end->modify('first day of this month')->modify('-11 months');
        $from = $start->format('Y-m-d');

        $sales = $this->byMonth(
            'SELECT DATE_FORMAT(invoiceDate, \'%Y-%m-01\') AS bucket, COALESCE(SUM(totalTtc), 0) AS value
             FROM invoices WHERE status <> \'CANCELLED\' AND invoiceDate BETWEEN :from AND :to
             GROUP BY bucket',
            $from, $to
        );
        $purchases = $this->byMonth(
            'SELECT DATE_FORMAT(orderDate, \'%Y-%m-01\') AS bucket, COALESCE(SUM(totalTtc), 0) AS value
             FROM purchaseOrders WHERE status <> \'CANCELLED\' AND orderDate BETWEEN :from AND :to
             GROUP BY bucket',
            $from, $to
        );
        $expenses = $this->byMonth(
            'SELECT DATE_FORMAT(expenseDate, \'%Y-%m-01\') AS bucket, COALESCE(SUM(amount), 0) AS value
             FROM expenses WHERE status IN (\'APPROVED\', \'PAID\') AND expenseDate BETWEEN :from AND :to
             GROUP BY bucket',
            $from, $to
        );
        $production = $this->byMonth(
            'SELECT DATE_FORMAT(completedAt, \'%Y-%m-01\') AS bucket,
                    COALESCE(SUM(materialCost + packagingCost + otherCost), 0) AS value
             FROM productionOrders WHERE status = \'COMPLETED\' AND DATE(completedAt) BETWEEN :from AND :to
             GROUP BY bucket',
            $from, $to
        );
        $quantities = $this->byMonth(
            'SELECT DATE_FORMAT(completedAt, \'%Y-%m-01\') AS bucket, COALESCE(SUM(actualQuantity), 0) AS value
             FROM productionOrders WHERE status = \'COMPLETED\' AND DATE(completedAt) BETWEEN :from AND :to
             GROUP BY bucket',
            $from, $to
        );

        $points = [];
        $cursor = $start;
        for ($index = 0; $index < 12; $index++) {
            $key = $cursor->format('Y-m-01');
            $points[] = [
                'month' => $key,
                'sales' => $sales[$key] ?? 0.0,
                'purchases' => $purchases[$key] ?? 0.0,
                'expenses' => $expenses[$key] ?? 0.0,
                'productionCost' => $production[$key] ?? 0.0,
                'productionQuantity' => $quantities[$key] ?? 0.0,
            ];
            $cursor = $cursor->modify('+1 month');
        }

        return $points;
    }

    private function salesByCategory(string $from, string $to): array
    {
        return $this->labelled(
            'SELECT c.nameFr AS label, COALESCE(SUM(ii.lineTotal), 0) AS value
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId AND i.status <> \'CANCELLED\'
             JOIN products p ON p.id = ii.productId
             JOIN productCategories c ON c.id = p.categoryId
             WHERE i.invoiceDate BETWEEN :from AND :to
             GROUP BY c.nameFr ORDER BY value DESC LIMIT 8',
            ['from' => $from, 'to' => $to]
        );
    }

    private function inventoryDistribution(): array
    {
        return $this->labelled(
            'SELECT c.nameFr AS label, COALESCE(SUM(b.quantity * b.unitCost), 0) AS value
             FROM productBatches b
             JOIN products p ON p.id = b.productId
             JOIN productCategories c ON c.id = p.categoryId
             WHERE b.qualityStatus = \'RELEASED\'
             GROUP BY c.nameFr HAVING value > 0 ORDER BY value DESC LIMIT 8'
        );
    }

    private function productRanking(string $from, string $to, string $orderBy): array
    {
        $order = $orderBy === 'profit' ? 'profit' : 'quantity';
        $rows = $this->db->select(
            'SELECT p.sku, p.nameFr AS name, c.nameFr AS category,
                    SUM(ii.quantity) AS quantity,
                    SUM(ii.lineTotal) AS revenue,
                    SUM(ii.lineTotal - (ii.quantity * p.productionCost)) AS profit
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId AND i.status <> \'CANCELLED\'
             JOIN products p ON p.id = ii.productId
             JOIN productCategories c ON c.id = p.categoryId
             WHERE i.invoiceDate BETWEEN :from AND :to
             GROUP BY p.id, p.sku, p.nameFr, c.nameFr
             ORDER BY ' . $order . ' DESC
             LIMIT 5',
            ['from' => $from, 'to' => $to]
        );

        return array_map(static fn (array $row): array => [
            'sku' => (string) $row['sku'],
            'name' => (string) $row['name'],
            'category' => (string) $row['category'],
            'quantity' => round((float) $row['quantity'], 3),
            'revenue' => round((float) $row['revenue'], 2),
            'profit' => round((float) $row['profit'], 2),
        ], $rows);
    }

    private function topCustomers(string $from, string $to): array
    {
        $rows = $this->db->select(
            'SELECT cu.name AS customer, cu.wilaya, COALESCE(SUM(i.totalTtc), 0) AS revenue,
                    cu.balance, COUNT(i.id) AS orders
             FROM invoices i
             JOIN customers cu ON cu.id = i.customerId
             WHERE i.status <> \'CANCELLED\' AND i.invoiceDate BETWEEN :from AND :to
             GROUP BY cu.id, cu.name, cu.wilaya, cu.balance
             ORDER BY revenue DESC LIMIT 5',
            ['from' => $from, 'to' => $to]
        );

        return array_map(static fn (array $row): array => [
            'customer' => (string) $row['customer'],
            'wilaya' => (string) ($row['wilaya'] ?? ''),
            'revenue' => round((float) $row['revenue'], 2),
            'balance' => round((float) $row['balance'], 2),
            'orders' => (int) $row['orders'],
        ], $rows);
    }

    // ------------------------------------------------------------------ alerts and activity

    private function lowStockAlerts(): array
    {
        $rows = $this->db->select(
            'SELECT v.itemKind, v.itemId, v.itemCode, v.itemName, v.unit, v.quantity, v.minStock,
                    COALESCE(c.nameFr, \'\') AS category
             FROM vwLowStock v
             LEFT JOIN products p ON v.itemKind = \'PRODUCT\' AND p.id = v.itemId
             LEFT JOIN productCategories c ON c.id = p.categoryId
             ORDER BY (v.minStock - v.quantity) DESC
             LIMIT 8'
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['itemId'],
            'type' => (float) $row['quantity'] <= 0 ? 'OUT_OF_STOCK' : 'LOW_STOCK',
            'itemCode' => (string) $row['itemCode'],
            'itemName' => (string) $row['itemName'],
            'category' => (string) $row['category'],
            'quantity' => round((float) $row['quantity'], 3),
            'threshold' => round((float) $row['minStock'], 3),
            'unit' => (string) $row['unit'],
            'expiryDate' => null,
            'warehouse' => '',
        ], $rows);
    }

    private function expiryAlerts(): array
    {
        $rows = $this->db->select(
            'SELECT b.id, m.code, m.commercialName, b.quantity, b.unit, b.expiryDate,
                    w.name AS warehouse, c.nameFr AS category
             FROM materialBatches b
             JOIN rawMaterials m ON m.id = b.materialId
             JOIN warehouses w ON w.id = b.warehouseId
             LEFT JOIN rawMaterialCategories c ON c.id = m.categoryId
             WHERE b.expiryDate IS NOT NULL AND b.quantity > 0
               AND b.expiryDate <= DATE_ADD(CURDATE(), INTERVAL 60 DAY)
             ORDER BY b.expiryDate ASC
             LIMIT 8'
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'type' => strtotime((string) $row['expiryDate']) < strtotime('today') ? 'EXPIRED' : 'EXPIRING',
            'itemCode' => (string) $row['code'],
            'itemName' => (string) $row['commercialName'],
            'category' => (string) ($row['category'] ?? ''),
            'quantity' => round((float) $row['quantity'], 3),
            'threshold' => 0,
            'unit' => (string) $row['unit'],
            'expiryDate' => $row['expiryDate'],
            'warehouse' => (string) $row['warehouse'],
        ], $rows);
    }

    private function recentActivities(): array
    {
        $rows = $this->db->select(
            'SELECT id, occurredAt, username, action, module, reference, severity
             FROM auditLogs ORDER BY occurredAt DESC, id DESC LIMIT 8'
        );

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'occurredAt' => date('c', strtotime((string) $row['occurredAt'])),
            'username' => (string) $row['username'],
            'action' => (string) $row['action'],
            'module' => (string) $row['module'],
            'reference' => (string) $row['reference'],
            'severity' => (string) $row['severity'],
        ], $rows);
    }

    // ------------------------------------------------------------------ helpers

    /** @return array<string,float> */
    private function byMonth(string $sql, string $from, string $to): array
    {
        $values = [];
        foreach ($this->db->select($sql, ['from' => $from, 'to' => $to]) as $row) {
            $values[(string) $row['bucket']] = round((float) $row['value'], 2);
        }

        return $values;
    }

    /** @return array<int,array{label:string,value:float}> */
    private function labelled(string $sql, array $params = []): array
    {
        $rows = $this->db->select($sql, $params);

        return array_map(static fn (array $row): array => [
            'label' => (string) $row['label'],
            'value' => round((float) $row['value'], 2),
        ], $rows);
    }

    private function scalarValue(string $sql, array $params = []): float
    {
        return round((float) $this->db->scalar($sql, $params), 2);
    }
}
