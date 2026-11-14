<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Validator;
use ChemManager\Api\Http\App;

/**
 * Inventory: warehouses, batches in FEFO order, the stock ledger, adjustments and transfers.
 *
 * <p>Every write goes through the ledger, so the screen can always answer "who moved what, when,
 * and why". Adjustments and transfers need their own permission and a written reason.</p>
 */
final class InventoryController
{
    public function __construct(private readonly App $app)
    {
    }

    public function warehouses(Request $request): Response
    {
        return Response::ok($this->app->inventory->warehouses());
    }

    public function summary(Request $request): Response
    {
        $expiryDays = $request->queryInt('expiringDays', 0);
        if ($expiryDays <= 0) {
            $expiryDays = $this->expiryWarningDays();
        }

        return Response::ok($this->app->inventory->summary($expiryDays));
    }

    public function stock(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->inventory->paginateStock(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryInt('warehouseId') > 0 ? $request->queryInt('warehouseId') : null,
            $request->queryString('kind'),
            $request->queryString('qualityStatus'),
            $request->queryInt('expiringDays', 0),
            in_array($request->queryString('availableOnly'), ['1', 'true', 'yes'], true)
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function movements(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->inventory->paginateMovements(
            $page,
            $pageSize,
            $request->queryString('kind'),
            $request->queryInt('batchId') > 0 ? $request->queryInt('batchId') : null,
            $request->queryInt('warehouseId') > 0 ? $request->queryInt('warehouseId') : null,
            $request->queryString('movementType'),
            $this->optionalDate($request, 'from'),
            $this->optionalDate($request, 'to')
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function fefo(Request $request): Response
    {
        $kind = strtoupper($request->queryString('kind', 'MATERIAL'));
        $itemId = $request->queryInt('itemId');
        $quantity = (float) $request->queryString('quantity', '0');

        return Response::ok($this->app->inventory->fefo($kind, $itemId, $quantity));
    }

    public function adjust(Request $request): Response
    {
        (new Validator($request->body))
            ->required('itemKind')
            ->inList('itemKind', ['MATERIAL', 'PRODUCT'])
            ->integer('batchId', 1, PHP_INT_MAX)
            ->required('countedQuantity')
            ->decimal('countedQuantity', 0, 999999999)
            ->required('reason', 255)
            ->validate();
        if (!isset($request->body['batchId'])) {
            throw ApiException::validation(['batchId' => 'validation.required']);
        }

        $result = $this->app->inventory->adjust($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'ADJUST', 'INVENTORY', 'materialBatches', $result['batchId'],
            ['delta' => $result['delta'], 'countedQuantity' => $result['countedQuantity'],
                'reason' => $result['reason']], 'WARNING', $request->ip, $result['batchNumber']);

        return Response::ok($result);
    }

    public function transfer(Request $request): Response
    {
        (new Validator($request->body))
            ->required('itemKind')
            ->inList('itemKind', ['MATERIAL', 'PRODUCT'])
            ->integer('batchId', 1, PHP_INT_MAX)
            ->integer('targetWarehouseId', 1, PHP_INT_MAX)
            ->required('quantity')
            ->decimal('quantity', 0.001, 999999999)
            ->required('reason', 255)
            ->validate();
        if (!isset($request->body['batchId'], $request->body['targetWarehouseId'])) {
            throw ApiException::validation(['batchId' => 'validation.required',
                'targetWarehouseId' => 'validation.required']);
        }

        $result = $this->app->inventory->transfer($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'TRANSFER', 'INVENTORY', 'materialBatches', $result['batchId'],
            ['quantity' => $result['quantity'], 'targetWarehouseId' => $result['targetWarehouseId'],
                'reason' => $result['reason']], 'INFO', $request->ip, $result['batchNumber']);

        return Response::ok($result);
    }

    private function expiryWarningDays(): int
    {
        $value = $this->app->db->scalar('SELECT value FROM settings WHERE settingKey = :key',
            ['key' => 'stock.expiryWarningDays']);

        return is_numeric($value) ? max(1, (int) $value) : 60;
    }

    private function optionalDate(Request $request, string $key): ?string
    {
        $value = $request->queryString($key);
        if ($value === '') {
            return null;
        }
        $parsed = \DateTimeImmutable::createFromFormat('Y-m-d', $value);
        if ($parsed === false || $parsed->format('Y-m-d') !== $value) {
            throw ApiException::validation([$key => 'validation.date']);
        }

        return $value;
    }
}
