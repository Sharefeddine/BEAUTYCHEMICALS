<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Chemical safety: the sheet library, the handling instructions, the pictograms and the incidents.
 */
final class SafetyController
{
    public function __construct(private readonly App $app)
    {
    }

    public function overview(Request $request): Response
    {
        return Response::ok($this->app->safety->overview());
    }

    public function pictograms(Request $request): Response
    {
        return Response::ok($this->app->safety->pictograms());
    }

    public function materials(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->safety->materials($page, $pageSize, $request->queryString('search'),
            $request->queryString('risk'));

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function material(Request $request): Response
    {
        return Response::ok($this->app->safety->material((int) $request->params['id']));
    }

    public function sheets(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->safety->sheets($page, $pageSize, $request->queryString('search'),
            $request->queryString('documentType'),
            in_array($request->queryString('currentOnly'), ['1', 'true', 'yes'], true));

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function registerSheet(Request $request): Response
    {
        $material = $this->app->safety->registerSheet($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'SAFETY', 'safetyDataSheets',
            (int) $material['sheetId'], ['material' => $material['code'], 'revision' => $material['sheetRevision']],
            'INFO', $request->ip, $material['code']);

        return Response::created($material);
    }

    public function saveInstructions(Request $request): Response
    {
        $material = $this->app->safety->saveInstructions((int) $request->params['id'], $request->body,
            (int) $request->user['id']);
        $this->app->audit->log($request->user, 'UPDATE', 'SAFETY', 'safetyInstructions',
            (int) $request->params['id'], [], 'INFO', $request->ip, $material['code']);

        return Response::ok($material);
    }

    public function setPictograms(Request $request): Response
    {
        $codes = $request->body['pictograms'] ?? [];
        $material = $this->app->safety->setPictograms((int) $request->params['id'],
            is_array($codes) ? $codes : []);
        $this->app->audit->log($request->user, 'UPDATE', 'SAFETY', 'materialPictograms',
            (int) $request->params['id'], ['pictograms' => $material['hazardPictograms']], 'INFO', $request->ip,
            $material['code']);

        return Response::ok($material);
    }

    public function incidents(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $result = $this->app->safety->incidents($page, $pageSize, $request->queryString('status'),
            $request->queryString('severity'), $request->queryString('search'));

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
            'summary' => $result['summary'],
        ]);
    }

    public function reportIncident(Request $request): Response
    {
        $incident = $this->app->safety->reportIncident($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'SAFETY', 'safetyIncidents', (int) $incident['id'],
            ['type' => $incident['incidentType'], 'severity' => $incident['severity']], 'WARNING', $request->ip,
            $incident['incidentNumber']);

        return Response::created($incident);
    }

    public function changeIncidentStatus(Request $request): Response
    {
        $incident = $this->app->safety->changeIncidentStatus((int) $request->params['id'],
            (string) ($request->body['status'] ?? ''));
        $this->app->audit->log($request->user, 'UPDATE', 'SAFETY', 'safetyIncidents', (int) $incident['id'],
            ['status' => $incident['status']], 'WARNING', $request->ip, $incident['incidentNumber']);

        return Response::ok($incident);
    }
}
