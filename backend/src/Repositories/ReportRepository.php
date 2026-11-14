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
