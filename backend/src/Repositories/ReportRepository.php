<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * The three statements the accountant asks for: what each product earns, where the cash went, and
 * what the month actually produced. Everything is computed from the issued documents and the cash
 * book, so a statement can always be traced back to a document.
 */
final class ReportRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    /**
     * Margin per product over a window: issued invoices minus issued or applied credit notes.
     *
     * @return array<string,mixed>
     */
    public function margin(string $from, string $to, int $limit = 50): array
    {
        [$from, $to] = $this->window($from, $to);

        $rows = $this->db->select(
            'SELECT p.id AS productId, p.sku, p.nameFr, p.type, c.nameFr AS categoryName,
                    SUM(x.quantity) AS quantity,
                    SUM(x.revenue) AS revenue,
                    SUM(x.quantity * CASE WHEN p.type = \'MANUFACTURED\' THEN p.productionCost
                                          ELSE p.purchaseCost END) AS cost
             FROM (
                 SELECT ii.productId, ii.quantity, ii.lineTotal AS revenue
                 FROM invoiceItems ii JOIN invoices i ON i.id = ii.invoiceId
                 WHERE i.status IN (\'ISSUED\', \'PARTIAL\', \'PAID\') AND i.invoiceDate BETWEEN :fromA AND :toA
                 UNION ALL
                 SELECT cn.productId, -cn.quantity, -cn.lineTotal
                 FROM creditNoteItems cn JOIN creditNotes c ON c.id = cn.creditNoteId
                 WHERE c.status IN (\'ISSUED\', \'APPLIED\') AND c.creditDate BETWEEN :fromB AND :toB
             ) x
             JOIN products p ON p.id = x.productId
             JOIN productCategories c ON c.id = p.categoryId
             GROUP BY p.id, p.sku, p.nameFr, p.type, c.nameFr
             ORDER BY (SUM(x.revenue) - SUM(x.quantity * CASE WHEN p.type = \'MANUFACTURED\'
                                                             THEN p.productionCost
                                                             ELSE p.purchaseCost END)) DESC
             LIMIT :limit',
            ['fromA' => $from, 'toA' => $to, 'fromB' => $from, 'toB' => $to, 'limit' => $limit]
        );

        $lines = [];
        $revenue = 0.0;
        $cost = 0.0;
        foreach ($rows as $row) {
            $lineRevenue = round((float) $row['revenue'], 2);
            $lineCost = round((float) $row['cost'], 2);
            $margin = round($lineRevenue - $lineCost, 2);
            $revenue += $lineRevenue;
            $cost += $lineCost;
            $lines[] = [
                'productId' => (int) $row['productId'],
                'sku' => (string) $row['sku'],
                'nameFr' => (string) $row['nameFr'],
                'type' => (string) $row['type'],
                'categoryName' => (string) $row['categoryName'],
                'quantity' => round((float) $row['quantity'], 3),
                'revenue' => $lineRevenue,
                'cost' => $lineCost,
                'margin' => $margin,
                'marginPct' => $lineRevenue > 0 ? round($margin / $lineRevenue * 100, 2) : 0.0,
            ];
        }

        $byCategory = [];
        foreach ($lines as $line) {
            $key = $line['categoryName'];
            $byCategory[$key] ??= ['categoryName' => $key, 'revenue' => 0.0, 'cost' => 0.0, 'margin' => 0.0];
            $byCategory[$key]['revenue'] = round($byCategory[$key]['revenue'] + $line['revenue'], 2);
            $byCategory[$key]['cost'] = round($byCategory[$key]['cost'] + $line['cost'], 2);
            $byCategory[$key]['margin'] = round($byCategory[$key]['margin'] + $line['margin'], 2);
        }
        usort($byCategory, static fn (array $a, array $b): int => $b['margin'] <=> $a['margin']);

        return [
            'from' => $from,
            'to' => $to,
            'lines' => $lines,
            'byCategory' => array_values($byCategory),
            'totals' => [
                'revenue' => round($revenue, 2),
                'cost' => round($cost, 2),
                'margin' => round($revenue - $cost, 2),
                'marginPct' => $revenue > 0 ? round(($revenue - $cost) / $revenue * 100, 2) : 0.0,
            ],
        ];
    }

    /**
     * Cash flow month by month: what came in, what went out, and where it came from.
     *
     * @return array<string,mixed>
     */
    public function cashflow(string $from, string $to): array
    {
        [$from, $to] = $this->window($from, $to);

        $months = $this->db->select(
            'SELECT DATE_FORMAT(t.occurredAt, \'%Y-%m\') AS period,
                    SUM(CASE WHEN t.direction = \'IN\' THEN t.amount ELSE 0 END) AS totalIn,
                    SUM(CASE WHEN t.direction = \'OUT\' THEN t.amount ELSE 0 END) AS totalOut
             FROM cashTransactions t
             WHERE DATE(t.occurredAt) BETWEEN :fromMonth AND :toMonth
             GROUP BY DATE_FORMAT(t.occurredAt, \'%Y-%m\') ORDER BY period',
            ['fromMonth' => $from, 'toMonth' => $to]
        );

        $bySource = $this->db->select(
            'SELECT t.sourceType,
                    SUM(CASE WHEN t.direction = \'IN\' THEN t.amount ELSE 0 END) AS totalIn,
                    SUM(CASE WHEN t.direction = \'OUT\' THEN t.amount ELSE 0 END) AS totalOut
             FROM cashTransactions t
             WHERE DATE(t.occurredAt) BETWEEN :fromSource AND :toSource
             GROUP BY t.sourceType ORDER BY totalIn DESC',
            ['fromSource' => $from, 'toSource' => $to]
        );

        $timeline = [];
        foreach ($months as $row) {
            $in = round((float) $row['totalIn'], 2);
            $out = round((float) $row['totalOut'], 2);
            $timeline[] = [
                'period' => (string) $row['period'],
                'totalIn' => $in,
                'totalOut' => $out,
                'net' => round($in - $out, 2),
            ];
        }

        $sources = [];
        foreach ($bySource as $row) {
            $in = round((float) $row['totalIn'], 2);
            $out = round((float) $row['totalOut'], 2);
            $sources[] = [
                'sourceType' => (string) $row['sourceType'],
                'totalIn' => $in,
                'totalOut' => $out,
                'net' => round($in - $out, 2),
            ];
        }

        $totalIn = array_sum(array_column($timeline, 'totalIn'));
        $totalOut = array_sum(array_column($timeline, 'totalOut'));

        return [
            'from' => $from,
            'to' => $to,
            'timeline' => $timeline,
            'bySource' => $sources,
            'totals' => [
                'totalIn' => round($totalIn, 2),
                'totalOut' => round($totalOut, 2),
                'net' => round($totalIn - $totalOut, 2),
            ],
        ];
    }

    /**
     * The result of a month: sales minus the cost of what was sold, minus the operating expenses.
     *
     * @return array<string,mixed>
     */
    public function result(string $period): array
    {
        if (preg_match('/^(\d{4})-(\d{2})$/', $period, $parts) !== 1
            || !checkdate((int) $parts[2], 1, (int) $parts[1])) {
            throw ApiException::validation(['period' => 'validation.format']);
        }
        $start = $period . '-01';
        $end = date('Y-m-t', (int) strtotime($start));

        $revenue = $this->db->first(
            'SELECT COALESCE(SUM(amountHt), 0) AS revenue, COALESCE(SUM(vatAmount), 0) AS vat,
                    COUNT(*) AS invoiceCount
             FROM invoices WHERE status IN (\'ISSUED\', \'PARTIAL\', \'PAID\')
               AND invoiceDate BETWEEN :fromRevenue AND :toRevenue',
            ['fromRevenue' => $start, 'toRevenue' => $end]
        ) ?? [];
        $credits = $this->db->first(
            'SELECT COALESCE(SUM(amountHt), 0) AS creditHt, COALESCE(SUM(vatAmount), 0) AS creditVat, COUNT(*) AS creditCount
             FROM creditNotes WHERE status IN (\'ISSUED\', \'APPLIED\')
               AND creditDate BETWEEN :fromCredit AND :toCredit',
            ['fromCredit' => $start, 'toCredit' => $end]
        ) ?? [];
        $costOfGoods = (float) $this->db->scalar(
            'SELECT COALESCE(SUM(ii.quantity * CASE WHEN p.type = \'MANUFACTURED\' THEN p.productionCost
                                                    ELSE p.purchaseCost END), 0)
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId
             JOIN products p ON p.id = ii.productId
             WHERE i.status IN (\'ISSUED\', \'PARTIAL\', \'PAID\') AND i.invoiceDate BETWEEN :fromCost AND :toCost',
            ['fromCost' => $start, 'toCost' => $end]
        );
        $expenses = $this->db->first(
            'SELECT COALESCE(SUM(amount), 0) AS amount, COALESCE(SUM(vatAmount), 0) AS vat, COUNT(*) AS expenseCount
             FROM expenses WHERE status IN (\'APPROVED\', \'PAID\') AND deletedAt IS NULL
               AND expenseDate BETWEEN :fromExpense AND :toExpense',
            ['fromExpense' => $start, 'toExpense' => $end]
        ) ?? [];
        $byCategory = $this->db->select(
            'SELECT c.code, c.nameFr, COALESCE(SUM(e.amount), 0) AS amount, COUNT(e.id) AS expenseCount
             FROM expenses e JOIN expenseCategories c ON c.id = e.categoryId
             WHERE e.status IN (\'APPROVED\', \'PAID\') AND e.deletedAt IS NULL
               AND e.expenseDate BETWEEN :fromCategory AND :toCategory
             GROUP BY c.id, c.code, c.nameFr ORDER BY amount DESC',
            ['fromCategory' => $start, 'toCategory' => $end]
        );

        $income = round((float) ($revenue['revenue'] ?? 0) - (float) ($credits['creditHt'] ?? 0), 2);
        $goods = round($costOfGoods, 2);
        $grossMargin = round($income - $goods, 2);
        $operating = round((float) ($expenses['amount'] ?? 0), 2);

        return [
            'period' => $period,
            'from' => $start,
            'to' => $end,
            'income' => [
                'invoicedHt' => round((float) ($revenue['revenue'] ?? 0), 2),
                'creditNotesHt' => round((float) ($credits['creditHt'] ?? 0), 2),
                'revenue' => $income,
                'vatCollected' => round((float) ($revenue['vat'] ?? 0) - (float) ($credits['creditVat'] ?? 0), 2),
                'invoiceCount' => (int) ($revenue['invoiceCount'] ?? 0),
                'creditCount' => (int) ($credits['creditCount'] ?? 0),
            ],
            'costOfGoods' => $goods,
            'grossMargin' => $grossMargin,
            'grossMarginPct' => $income > 0 ? round($grossMargin / $income * 100, 2) : 0.0,
            'expenses' => [
                'total' => $operating,
                'vat' => round((float) ($expenses['vat'] ?? 0), 2),
                'count' => (int) ($expenses['expenseCount'] ?? 0),
                'byCategory' => array_map(static fn (array $row): array => [
                    'code' => (string) $row['code'],
                    'nameFr' => (string) $row['nameFr'],
                    'amount' => round((float) $row['amount'], 2),
                    'expenseCount' => (int) $row['expenseCount'],
                ], $byCategory),
            ],
            'result' => round($grossMargin - $operating, 2),
            'resultPct' => $income > 0 ? round(($grossMargin - $operating) / $income * 100, 2) : 0.0,
        ];
    }

    /**
     * The reports the client may ask for, in the order the report centre shows them.
     *
     * @return array<int,array<string,mixed>>
     */
    public function catalogue(): array
    {
        return [
            ['key' => 'result', 'group' => 'FINANCE', 'periodic' => true],
            ['key' => 'margin', 'group' => 'FINANCE', 'periodic' => true],
            ['key' => 'cashflow', 'group' => 'FINANCE', 'periodic' => true],
            ['key' => 'sales', 'group' => 'COMMERCE', 'periodic' => true],
            ['key' => 'purchases', 'group' => 'COMMERCE', 'periodic' => true],
            ['key' => 'stock', 'group' => 'OPERATIONS', 'periodic' => false],
            ['key' => 'production', 'group' => 'OPERATIONS', 'periodic' => true],
            ['key' => 'safety', 'group' => 'SAFETY', 'periodic' => false],
        ];
    }

    /**
     * Sales report: what was invoiced in the window, month by month, per customer and per product.
     *
     * @return array<string,mixed>
     */
    public function sales(string $from, string $to): array
    {
        [$from, $to] = $this->window($from, $to);
        $params = ['fromTotals' => $from, 'toTotals' => $to];

        $totals = $this->db->first(
            'SELECT COUNT(*) AS invoiceCount, COALESCE(SUM(amountHt), 0) AS amountHt,
                    COALESCE(SUM(vatAmount), 0) AS vatAmount, COALESCE(SUM(totalTtc), 0) AS totalTtc,
                    COALESCE(SUM(paidAmount), 0) AS paidAmount
             FROM invoices WHERE status IN (\'ISSUED\', \'PARTIAL\', \'PAID\')
               AND invoiceDate BETWEEN :fromTotals AND :toTotals',
            $params
        ) ?? [];

        $byMonth = $this->db->select(
            'SELECT DATE_FORMAT(invoiceDate, \'%Y-%m\') AS period, COUNT(*) AS invoiceCount,
                    COALESCE(SUM(amountHt), 0) AS amountHt, COALESCE(SUM(totalTtc), 0) AS totalTtc
             FROM invoices WHERE status IN (\'ISSUED\', \'PARTIAL\', \'PAID\')
               AND invoiceDate BETWEEN :fromMonth AND :toMonth
             GROUP BY DATE_FORMAT(invoiceDate, \'%Y-%m\') ORDER BY period',
            ['fromMonth' => $from, 'toMonth' => $to]
        );

        $byCustomer = $this->db->select(
            'SELECT c.id AS customerId, c.name, COUNT(i.id) AS invoiceCount,
                    COALESCE(SUM(i.amountHt), 0) AS amountHt, COALESCE(SUM(i.totalTtc), 0) AS totalTtc,
                    COALESCE(SUM(i.totalTtc - i.paidAmount), 0) AS outstanding
             FROM invoices i JOIN customers c ON c.id = i.customerId
             WHERE i.status IN (\'ISSUED\', \'PARTIAL\', \'PAID\') AND i.invoiceDate BETWEEN :fromCustomer AND :toCustomer
             GROUP BY c.id, c.name ORDER BY amountHt DESC LIMIT 20',
            ['fromCustomer' => $from, 'toCustomer' => $to]
        );

        $byProduct = $this->db->select(
            'SELECT p.id AS productId, p.sku, p.nameFr, SUM(ii.quantity) AS quantity,
                    COALESCE(SUM(ii.lineTotal), 0) AS revenue
             FROM invoiceItems ii
             JOIN invoices i ON i.id = ii.invoiceId
             JOIN products p ON p.id = ii.productId
             WHERE i.status IN (\'ISSUED\', \'PARTIAL\', \'PAID\') AND i.invoiceDate BETWEEN :fromProduct AND :toProduct
             GROUP BY p.id, p.sku, p.nameFr ORDER BY revenue DESC LIMIT 20',
            ['fromProduct' => $from, 'toProduct' => $to]
        );

        return [
            'from' => $from,
            'to' => $to,
            'totals' => [
                'invoiceCount' => (int) ($totals['invoiceCount'] ?? 0),
                'amountHt' => round((float) ($totals['amountHt'] ?? 0), 2),
                'vatAmount' => round((float) ($totals['vatAmount'] ?? 0), 2),
                'totalTtc' => round((float) ($totals['totalTtc'] ?? 0), 2),
                'paidAmount' => round((float) ($totals['paidAmount'] ?? 0), 2),
                'outstanding' => round((float) ($totals['totalTtc'] ?? 0) - (float) ($totals['paidAmount'] ?? 0), 2),
            ],
            'byMonth' => $byMonth,
            'byCustomer' => $byCustomer,
            'byProduct' => $byProduct,
        ];
    }

    /**
     * Purchases report: what was ordered and received in the window, per supplier and per month.
     *
     * @return array<string,mixed>
     */
    public function purchases(string $from, string $to): array
    {
        [$from, $to] = $this->window($from, $to);

        $totals = $this->db->first(
            'SELECT COUNT(*) AS orderCount, COALESCE(SUM(amountHt), 0) AS amountHt,
                    COALESCE(SUM(vatAmount), 0) AS vatAmount, COALESCE(SUM(totalTtc), 0) AS totalTtc
             FROM purchaseOrders WHERE status <> \'CANCELLED\' AND orderDate BETWEEN :fromTotals AND :toTotals',
            ['fromTotals' => $from, 'toTotals' => $to]
        ) ?? [];

        $byStatus = $this->db->select(
            'SELECT status, COUNT(*) AS orderCount, COALESCE(SUM(totalTtc), 0) AS totalTtc
             FROM purchaseOrders WHERE orderDate BETWEEN :fromStatus AND :toStatus
             GROUP BY status ORDER BY orderCount DESC',
            ['fromStatus' => $from, 'toStatus' => $to]
        );

        $byMonth = $this->db->select(
            'SELECT DATE_FORMAT(orderDate, \'%Y-%m\') AS period, COUNT(*) AS orderCount,
                    COALESCE(SUM(totalTtc), 0) AS totalTtc
             FROM purchaseOrders WHERE status <> \'CANCELLED\' AND orderDate BETWEEN :fromMonth AND :toMonth
             GROUP BY DATE_FORMAT(orderDate, \'%Y-%m\') ORDER BY period',
            ['fromMonth' => $from, 'toMonth' => $to]
        );

        $bySupplier = $this->db->select(
            'SELECT s.id AS supplierId, s.name, COUNT(o.id) AS orderCount,
                    COALESCE(SUM(o.totalTtc), 0) AS totalTtc
             FROM purchaseOrders o JOIN suppliers s ON s.id = o.supplierId
             WHERE o.status <> \'CANCELLED\' AND o.orderDate BETWEEN :fromSupplier AND :toSupplier
             GROUP BY s.id, s.name ORDER BY totalTtc DESC LIMIT 20',
            ['fromSupplier' => $from, 'toSupplier' => $to]
        );

        $received = $this->db->first(
            'SELECT COUNT(*) AS receiptCount,
                    COALESCE(SUM((SELECT COALESCE(SUM(gi.quantity), 0) FROM goodsReceiptItems gi
                                  WHERE gi.receiptId = r.id)), 0) AS quantity
             FROM goodsReceipts r WHERE DATE(r.receivedAt) BETWEEN :fromReceipt AND :toReceipt',
            ['fromReceipt' => $from, 'toReceipt' => $to]
        ) ?? [];

        return [
            'from' => $from,
            'to' => $to,
            'totals' => [
                'orderCount' => (int) ($totals['orderCount'] ?? 0),
                'amountHt' => round((float) ($totals['amountHt'] ?? 0), 2),
                'vatAmount' => round((float) ($totals['vatAmount'] ?? 0), 2),
                'totalTtc' => round((float) ($totals['totalTtc'] ?? 0), 2),
                'receiptCount' => (int) ($received['receiptCount'] ?? 0),
                'receivedQuantity' => round((float) ($received['quantity'] ?? 0), 3),
            ],
            'byStatus' => $byStatus,
            'byMonth' => $byMonth,
            'bySupplier' => $bySupplier,
        ];
    }

    /**
     * Stock report: what the warehouse is worth, what is expiring, what is in quarantine.
     *
     * @return array<string,mixed>
     */
    public function stock(int $expiryDays = 90, int $quantityLimit = 40): array
    {
        $warehouses = $this->db->select(
            'SELECT w.id, w.code, w.name,
                    COALESCE((SELECT SUM(b.quantity * b.unitCost) FROM productBatches b
                              WHERE b.warehouseId = w.id), 0) AS productValue,
                    COALESCE((SELECT SUM(mb.quantity * mb.purchasePrice) FROM materialBatches mb
                              WHERE mb.warehouseId = w.id), 0) AS materialValue
             FROM warehouses w WHERE w.deletedAt IS NULL ORDER BY w.name'
        );
        $total = 0.0;
        foreach ($warehouses as &$warehouse) {
            $warehouse['totalValue'] = round((float) $warehouse['productValue']
                + (float) $warehouse['materialValue'], 2);
            $total += $warehouse['totalValue'];
        }
        unset($warehouse);

        $expiring = $this->db->select(
            'SELECT p.nameFr AS productName, b.batchNumber, w.name AS warehouseName, b.quantity,
                    b.expiryDate, DATEDIFF(b.expiryDate, CURDATE()) AS daysLeft, b.qualityStatus
             FROM productBatches b
             JOIN products p ON p.id = b.productId
             JOIN warehouses w ON w.id = b.warehouseId
             WHERE b.quantity > 0 AND b.expiryDate IS NOT NULL
               AND b.expiryDate <= DATE_ADD(CURDATE(), INTERVAL ' . (int) $expiryDays . ' DAY)
             ORDER BY b.expiryDate LIMIT :limit',
            ['limit' => $quantityLimit]
        );
        $expired = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM productBatches WHERE quantity > 0 AND expiryDate IS NOT NULL
             AND expiryDate < CURDATE()'
        );
        $quarantine = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM productBatches WHERE quantity > 0 AND qualityStatus = \'QUARANTINE\''
        );
        $lowStock = $this->db->select(
            'SELECT p.sku, p.nameFr, p.minStock, COALESCE(SUM(b.quantity), 0) AS quantity
             FROM products p
             LEFT JOIN productBatches b ON b.productId = p.id
             WHERE p.deletedAt IS NULL AND p.isActive = 1 AND p.type = \'MANUFACTURED\'
             GROUP BY p.id, p.sku, p.nameFr, p.minStock
             HAVING quantity < p.minStock
             ORDER BY quantity LIMIT :limit',
            ['limit' => $quantityLimit]
        );

        return [
            'warehouses' => $warehouses,
            'totals' => [
                'totalValue' => round($total, 2),
                'expiringCount' => count($expiring),
                'expiredCount' => $expired,
                'quarantineCount' => $quarantine,
                'lowStockCount' => count($lowStock),
            ],
            'expiring' => $expiring,
            'lowStock' => $lowStock,
        ];
    }

    /**
     * Production report: what the workshop was asked for, what it produced, what it wasted.
     *
     * @return array<string,mixed>
     */
    public function production(string $from, string $to): array
    {
        [$from, $to] = $this->window($from, $to);

        $totals = $this->db->first(
            'SELECT COUNT(*) AS orderCount, COALESCE(SUM(plannedQuantity), 0) AS plannedQuantity,
                    COALESCE(SUM(actualQuantity), 0) AS actualQuantity, COALESCE(SUM(wasteQuantity), 0) AS wasteQuantity,
                    COALESCE(SUM(materialCost + packagingCost + otherCost), 0) AS totalCost
             FROM productionOrders WHERE plannedDate BETWEEN :fromTotals AND :toTotals
               AND status <> \'CANCELLED\'',
            ['fromTotals' => $from, 'toTotals' => $to]
        ) ?? [];

        $byStatus = $this->db->select(
            'SELECT status, COUNT(*) AS orderCount, COALESCE(SUM(actualQuantity), 0) AS actualQuantity
             FROM productionOrders WHERE plannedDate BETWEEN :fromStatus AND :toStatus
             GROUP BY status ORDER BY orderCount DESC',
            ['fromStatus' => $from, 'toStatus' => $to]
        );

        $byProduct = $this->db->select(
            'SELECT p.sku, p.nameFr, COUNT(o.id) as orderCount,
                    COALESCE(SUM(o.plannedQuantity), 0) AS plannedQuantity,
                    COALESCE(SUM(o.actualQuantity), 0) AS actualQuantity,
                    COALESCE(SUM(o.wasteQuantity), 0) AS wasteQuantity,
                    COALESCE(SUM(o.materialCost + o.packagingCost + o.otherCost), 0) AS totalCost
             FROM productionOrders o JOIN products p ON p.id = o.productId
             WHERE o.plannedDate BETWEEN :fromProduct AND :toProduct AND o.status <> \'CANCELLED\'
             GROUP BY p.sku, p.nameFr ORDER BY actualQuantity DESC LIMIT 20',
            ['fromProduct' => $from, 'toProduct' => $to]
        );

        $planned = (float) ($totals['plannedQuantity'] ?? 0);
        $actual = (float) ($totals['actualQuantity'] ?? 0);

        return [
            'from' => $from,
            'to' => $to,
            'totals' => [
                'orderCount' => (int) ($totals['orderCount'] ?? 0),
                'plannedQuantity' => round($planned, 3),
                'actualQuantity' => round($actual, 3),
                'wasteQuantity' => round((float) ($totals['wasteQuantity'] ?? 0), 3),
                'totalCost' => round((float) ($totals['totalCost'] ?? 0), 2),
                'yieldPct' => $planned > 0 ? round($actual / $planned * 100, 2) : 0.0,
                'unitCost' => $actual > 0 ? round((float) ($totals['totalCost'] ?? 0) / $actual, 2) : 0.0,
            ],
            'byStatus' => $byStatus,
            'byProduct' => $byProduct,
        ];
    }

    /**
     * Safety report: how much of the chemical register is documented, and what happened.
     *
     * @return array<string,mixed>
     */
    public function safety(): array
    {
        $materials = $this->db->first(
            'SELECT COUNT(*) AS total, SUM(CASE WHEN hazardClass <> \'\' THEN 1 ELSE 0 END) AS hazardous,
                    SUM(CASE WHEN hazardClass <> \'\' AND sdsAvailable = 0 THEN 1 ELSE 0 END) AS withoutSheet
             FROM rawMaterials WHERE deletedAt IS NULL AND isActive = 1'
        ) ?? [];
        $obsolete = (int) $this->db->scalar(
            'SELECT COUNT(*) FROM safetyDataSheets WHERE isCurrent = 1 AND documentType = \'SDS\'
               AND (issueDate IS NULL OR issueDate < DATE_SUB(CURDATE(), INTERVAL 3 YEAR))'
        );
        $bySeverity = $this->db->select(
            'SELECT severity, status, COUNT(*) AS incidentCount FROM safetyIncidents
             GROUP BY severity, status ORDER BY severity'
        );
        $byType = $this->db->select(
            'SELECT incidentType, COUNT(*) AS incidentCount FROM safetyIncidents GROUP BY incidentType'
        );
        $missing = $this->db->select(
            'SELECT m.code, m.commercialName, m.hazardClass FROM rawMaterials m
             WHERE m.deletedAt IS NULL AND m.isActive = 1 AND m.hazardClass <> \'\' AND m.sdsAvailable = 0
             ORDER BY m.commercialName LIMIT 50'
        );

        return [
            'materials' => [
                'total' => (int) ($materials['total'] ?? 0),
                'hazardous' => (int) ($materials['hazardous'] ?? 0),
                'withoutSheet' => (int) ($materials['withoutSheet'] ?? 0),
                'obsoleteSheets' => $obsolete,
            ],
            'incidents' => [
                'bySeverity' => $bySeverity,
                'byType' => $byType,
                'total' => array_sum(array_map(static fn (array $row): int => (int) $row['incidentCount'], $byType)),
            ],
            'missingSheets' => $missing,
        ];
    }

    /**
     * A window is a pair of real dates: the parser is lenient (2026-13-01 rolls over to January),
     * so the calendar itself is checked.
     *
     * @return array{0:string,1:string}
     */
    private function window(string $from, string $to): array
    {
        $start = $this->realDate($from !== '' ? $from : date('Y-m-01'), 'from');
        $end = $this->realDate($to !== '' ? $to : date('Y-m-t'), 'to');
        if ($end < $start) {
            throw ApiException::validation(['to' => 'validation.after_or_equal']);
        }

        return [$start, $end];
    }

    private function realDate(string $value, string $field): string
    {
        if (preg_match('/^(\d{4})-(\d{2})-(\d{2})$/', trim($value), $parts) !== 1
            || !checkdate((int) $parts[2], (int) $parts[3], (int) $parts[1])) {
            throw ApiException::validation([$field => 'validation.date']);
        }

        return sprintf('%04d-%02d-%02d', (int) $parts[1], (int) $parts[2], (int) $parts[3]);
    }
}
