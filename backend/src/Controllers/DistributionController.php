<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * The fleet and the daily tours.
 */
final class DistributionController
{
    public function __construct(private readonly App $app)
    {
    }

    public function vehicles(Request $request): Response
    {
        return Response::ok($this->app->distribution->vehicles());
    }

    public function storeVehicle(Request $request): Response
    {
        $vehicle = $this->app->distribution->createVehicle($request->body);
        $this->app->audit->log($request->user, 'CREATE', 'DISTRIBUTION', 'vehicles', $vehicle['id'],
            ['plate' => $vehicle['plate']], 'INFO', $request->ip, $vehicle['plate']);

        return Response::created($vehicle);
    }

    public function updateVehicle(Request $request): Response
    {
        $vehicle = $this->app->distribution->updateVehicle((int) $request->params['id'], $request->body);
        $this->app->audit->log($request->user, 'UPDATE', 'DISTRIBUTION', 'vehicles', $vehicle['id'],
            ['active' => $vehicle['isActive']], 'INFO', $request->ip, $vehicle['plate']);

        return Response::ok($vehicle);
    }

    public function drivers(Request $request): Response
    {
        return Response::ok($this->app->distribution->drivers());
    }

    public function routes(Request $request): Response
    {
        $page = max(1, $request->queryInt('page', 1));
        $pageSize = min(200, max(1, $request->queryInt('pageSize', 25)));
        $status = $request->queryString('status');
        $date = $request->queryString('routeDate');
        $result = $this->app->distribution->paginateRoutes(
            $page,
            $pageSize,
            $request->queryString('search'),
            $status !== '' ? $status : null,
            $date !== '' ? $date : null
        );

        return Response::ok($result['rows'], [
            'page' => $page,
            'pageSize' => $pageSize,
            'total' => $result['total'],
            'totalPages' => (int) ceil($result['total'] / max(1, $pageSize)),
        ]);
    }

    public function route(Request $request): Response
    {
        return Response::ok($this->app->distribution->route((int) $request->params['id']));
    }

    public function storeRoute(Request $request): Response
    {
        $route = $this->app->distribution->createRoute($request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'CREATE', 'DISTRIBUTION', 'deliveryRoutes', $route['id'],
            ['vehicle' => $route['plate'], 'stops' => $route['stopCount']], 'INFO', $request->ip,
            $route['routeNumber']);

        return Response::created($route);
    }

    public function loadRoute(Request $request): Response
    {
        $route = $this->app->distribution->loadRoute((int) $request->params['id']);
        $this->app->audit->log($request->user, 'LOAD', 'DISTRIBUTION', 'deliveryRoutes', $route['id'],
            ['status' => $route['status']], 'INFO', $request->ip, $route['routeNumber']);

        return Response::ok($route);
    }

    public function startRoute(Request $request): Response
    {
        $route = $this->app->distribution->startRoute((int) $request->params['id']);
        $this->app->audit->log($request->user, 'DEPART', 'DISTRIBUTION', 'deliveryRoutes', $route['id'],
            ['status' => $route['status']], 'INFO', $request->ip, $route['routeNumber']);

        return Response::ok($route);
    }

    public function deliverStop(Request $request): Response
    {
        $route = $this->app->distribution->deliverStop((int) $request->params['id'],
            (int) $request->params['stopId'], $request->body, (int) $request->user['id']);
        $this->app->audit->log($request->user, 'DELIVER_STOP', 'DISTRIBUTION', 'routeStops',
            (int) $request->params['stopId'], ['route' => $route['routeNumber']], 'INFO', $request->ip,
            $route['routeNumber']);

        return Response::ok($route);
    }

    public function completeRoute(Request $request): Response
    {
        $route = $this->app->distribution->completeRoute((int) $request->params['id'], $request->body);
        $this->app->audit->log($request->user, 'CLOSE', 'DISTRIBUTION', 'deliveryRoutes', $route['id'],
            ['delivered' => $route['deliveredCount'], 'stops' => $route['stopCount']], 'INFO', $request->ip,
            $route['routeNumber']);

        return Response::ok($route);
    }

    public function cancelRoute(Request $request): Response
    {
        $route = $this->app->distribution->cancelRoute((int) $request->params['id']);
        $this->app->audit->log($request->user, 'CANCEL', 'DISTRIBUTION', 'deliveryRoutes', $route['id'],
            ['status' => $route['status']], 'WARNING', $request->ip, $route['routeNumber']);

        return Response::ok($route);
    }
}
