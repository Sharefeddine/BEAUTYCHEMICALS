<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Formulations module: families, versions, bill of materials, approval and comparison.
 *
 * <p>Nothing here generates a formula. The endpoints store what a qualified user entered, compute
 * the cost from the current purchase prices and refuse to change an approved version.</p>
 */
final class FormulationController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $productId = $request->queryInt('productId') > 0 ? $request->queryInt('productId') : null;

        $result = $this->app->formulations->paginate(
            $page,
            $pageSize,
            $request->queryString('search'),
            $request->queryString('status'),
            $productId
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function show(Request $request): Response
    {
        return Response::ok($this->app->formulations->find((int) $request->params['id']));
    }

    public function store(Request $request): Response
    {
        $data = $this->validated($request);
        $created = $this->app->formulations->create($data, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'FORMULATIONS', 'formulations', $created['id'],
            ['code' => $created['code']], 'INFO', $request->ip, $created['code']);

        return Response::created($created);
    }

    public function update(Request $request): Response
    {
        $data = $this->validated($request, false);
        $updated = $this->app->formulations->update((int) $request->params['id'], $data, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'FORMULATIONS', 'formulations', $updated['id'],
            array_keys($data), 'INFO', $request->ip, $updated['code']);

        return Response::ok($updated);
    }

    public function archive(Request $request): Response
    {
        $archived = $this->app->formulations->archive((int) $request->params['id'], (int) $request->user['id']);
        $this->app->audit->log($request->user, 'ARCHIVE', 'FORMULATIONS', 'formulations', $archived['id'],
            [], 'WARNING', $request->ip, $archived['code']);

        return Response::ok($archived);
    }

    public function versions(Request $request): Response
    {
        $formulationId = (int) $request->params['id'];
        $this->app->formulations->find($formulationId);

        return Response::ok($this->app->formulations->versions($formulationId));
    }

    public function showVersion(Request $request): Response
    {
        return Response::ok($this->app->formulations->version(
            (int) $request->params['id'],
            (int) $request->params['versionId']
        ));
    }

    public function storeVersion(Request $request): Response
    {
        $formulationId = (int) $request->params['id'];
        $version = $this->app->formulations->createVersion($formulationId, $request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'FORMULATIONS', 'formulationVersions', $version['id'],
            ['version' => $version['version']], 'INFO', $request->ip, (string) $version['version']);

        return Response::created($version);
    }

    public function updateVersion(Request $request): Response
    {
        $version = $this->app->formulations->updateVersion(
            (int) $request->params['id'],
            (int) $request->params['versionId'],
            $request->body,
            (int) $request->user['id']
        );
        $this->app->audit->log($request->user, 'UPDATE', 'FORMULATIONS', 'formulationVersions', $version['id'],
            ['items' => $version['itemCount'], 'cost' => $version['cost']], 'INFO', $request->ip,
            (string) $version['version']);

        return Response::ok($version);
    }

    public function approve(Request $request): Response
    {
        $version = $this->app->formulations->approve(
            (int) $request->params['id'],
            (int) $request->params['versionId'],
            (int) $request->user['id']
        );
        $this->app->audit->log($request->user, 'APPROVE', 'FORMULATIONS', 'formulationVersions', $version['id'],
            ['version' => $version['version'], 'cost' => $version['cost']], 'WARNING', $request->ip,
            (string) $version['version']);

        return Response::ok($version);
    }

    public function compare(Request $request): Response
    {
        $left = $request->queryInt('left');
        $right = $request->queryInt('right');
        if ($left <= 0 || $right <= 0) {
            throw ApiException::validation(['left' => 'validation.required', 'right' => 'validation.required']);
        }

        return Response::ok($this->app->formulations->compare((int) $request->params['id'], $left, $right));
    }

    /**
     * Minimal validation: the free text is kept as entered, the identifiers must be usable. The
     * business rules (locked version, empty bill of materials) live in the repository so they also
     * hold for any future caller.
     */
    private function validated(Request $request, bool $creating = true): array
    {
        $data = $request->body;
        if ($creating) {
            if (trim((string) ($data['nameFr'] ?? '')) === '') {
                throw ApiException::validation(['nameFr' => 'validation.required']);
            }
            if (trim((string) ($data['nameAr'] ?? '')) === '') {
                $data['nameAr'] = (string) $data['nameFr'];
            }
        }

        return $data;
    }
}
