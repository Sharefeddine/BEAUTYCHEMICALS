<?php

declare(strict_types=1);

namespace ChemManager\Api\Http;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Audit;
use ChemManager\Api\Core\Config;
use ChemManager\Api\Core\Database;
use ChemManager\Api\Core\Logger;
use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Core\Router;
use ChemManager\Api\Repositories\AuditRepository;
use ChemManager\Api\Repositories\CustomerRepository;
use ChemManager\Api\Repositories\DashboardRepository;
use ChemManager\Api\Repositories\DistributionRepository;
use ChemManager\Api\Repositories\EmployeeRepository;
use ChemManager\Api\Repositories\ExpenseRepository;
use ChemManager\Api\Repositories\FinanceRepository;
use ChemManager\Api\Repositories\FormulationRepository;
use ChemManager\Api\Repositories\InventoryRepository;
use ChemManager\Api\Repositories\MaterialRepository;
use ChemManager\Api\Repositories\NotificationRepository;
use ChemManager\Api\Repositories\ProductRepository;
use ChemManager\Api\Repositories\ProductionRepository;
use ChemManager\Api\Repositories\PurchaseRepository;
use ChemManager\Api\Repositories\SalesRepository;
use ChemManager\Api\Repositories\SettingsRepository;
use ChemManager\Api\Repositories\SupplierRepository;
use ChemManager\Api\Repositories\UserRepository;
use ChemManager\Api\Security\Auth;
use ChemManager\Api\Security\Authorizer;
use Throwable;

/**
 * Composition root of the API: builds the object graph, applies CORS, authenticates, checks the
 * required permission, dispatches and renders the single JSON envelope.
 */
final class App
{
    public readonly Config $config;
    public readonly Database $db;
    public readonly Logger $logger;
    public readonly Audit $audit;
    public readonly Auth $auth;
    public readonly Authorizer $authorizer;
    public readonly UserRepository $users;
    public readonly ProductRepository $products;
    public readonly MaterialRepository $materials;
    public readonly FormulationRepository $formulations;
    public readonly InventoryRepository $inventory;
    public readonly PurchaseRepository $purchases;
    public readonly ProductionRepository $production;
    public readonly SupplierRepository $suppliers;
    public readonly NotificationRepository $notifications;
    public readonly SettingsRepository $settings;
    public readonly DashboardRepository $dashboard;
    public readonly AuditRepository $auditLogs;
    public readonly CustomerRepository $customers;
    public readonly SalesRepository $sales;
    public readonly DistributionRepository $distribution;
    public readonly FinanceRepository $finance;
    public readonly ExpenseRepository $expenses;
    public readonly EmployeeRepository $employees;
    public readonly Router $router;

    public function __construct(?Config $config = null)
    {
        $this->config = $config ?? Config::load();
        date_default_timezone_set((string) $this->config->get('app.timezone', 'Africa/Algiers'));

        $this->db = new Database($this->config);
        $this->logger = new Logger((string) $this->config->get('app.logFile'));
        $this->audit = new Audit($this->db);
        $this->auth = new Auth($this->db, $this->config, $this->audit);
        $this->authorizer = new Authorizer();

        $bcryptCost = (int) $this->config->get('security.bcryptCost');
        $this->users = new UserRepository($this->db, $bcryptCost);
        $this->products = new ProductRepository($this->db);
        $this->materials = new MaterialRepository($this->db);
        $this->formulations = new FormulationRepository($this->db);
        $this->inventory = new InventoryRepository($this->db);
        $this->purchases = new PurchaseRepository($this->db, $this->inventory);
        $this->production = new ProductionRepository($this->db, $this->inventory);
        $this->suppliers = new SupplierRepository($this->db);
        $this->notifications = new NotificationRepository($this->db);
        $this->settings = new SettingsRepository($this->db);
        $this->dashboard = new DashboardRepository($this->db);
        $this->auditLogs = new AuditRepository($this->db);
        $this->customers = new CustomerRepository($this->db);
        $this->finance = new FinanceRepository($this->db);
        $this->expenses = new ExpenseRepository($this->db, $this->finance);
        $this->employees = new EmployeeRepository($this->db, $this->finance, $this->expenses);
        $this->sales = new SalesRepository($this->db, $this->inventory, $this->customers, $this->finance);
        $this->distribution = new DistributionRepository($this->db, $this->sales);

        $this->router = new Router();
        (require dirname(__DIR__, 2) . '/src/routes.php')($this->router, $this);
    }

    public function handle(Request $request): Response
    {
        $started = microtime(true);
        $response = $this->dispatch($request);

        $this->logger->info('request', [
            'method' => $request->method,
            'path' => $request->path,
            'status' => $response->getStatus(),
            'user' => $request->user['username'] ?? null,
            'ms' => (int) round((microtime(true) - $started) * 1000),
        ]);

        return $response;
    }

    private function dispatch(Request $request): Response
    {
        try {
            $route = $this->router->match($request->method, $request->path);
            $request->params = $route['params'];

            $roles = [];
            if ($route['auth']) {
                $identity = $this->auth->authenticate($request);
                $request->user = $identity['user'];
                $request->permissions = $identity['permissions'];
                $roles = $this->auth->rolesFor((int) $identity['user']['id']);

                // A temporary password only allows changing it or signing out.
                if ($identity['user']['mustChangePassword']
                    && !in_array($request->path, ['/api/auth/password', '/api/auth/logout', '/api/auth/me'], true)) {
                    throw ApiException::passwordChangeRequired();
                }
            }

            $this->authorizer->assert($route['permission'], $request->permissions, $roles);

            $handler = $route['handler'];
            $result = $handler($request);

            return $result instanceof Response ? $result : Response::ok($result);
        } catch (ApiException $exception) {
            if ($exception->status >= 500) {
                $this->logger->error('api-exception', ['code' => $exception->errorCode, 'message' => $exception->getMessage()]);
            }

            return $this->renderException($exception);
        } catch (Throwable $exception) {
            $this->logger->error('unhandled', [
                'type' => $exception::class,
                'message' => $exception->getMessage(),
                'file' => basename($exception->getFile()) . ':' . $exception->getLine(),
            ]);

            return Response::error(500, 'INTERNAL_ERROR',
                $this->config->isDebug() ? $exception->getMessage() : 'An unexpected error occurred.');
        }
    }

    private function renderException(ApiException $exception): Response
    {
        $response = Response::error($exception->status, $exception->errorCode, $exception->getMessage(),
            $this->config->isDebug() || $exception->status < 500 ? $exception->details : []);
        if ($exception->errorCode === 'TOO_MANY_REQUESTS' && isset($exception->details['retryAfter'])) {
            $response->header('Retry-After', (string) $exception->details['retryAfter']);
        }

        return $response;
    }

    /** CORS for the desktop client (which sends no Origin) and for a future web front-end. */
    public function applyCors(Request $request): void
    {
        $origin = $request->header('origin');
        $allowed = (array) $this->config->get('app.allowedOrigins', []);
        if ($origin !== null && ($allowed === [] || in_array($origin, $allowed, true))) {
            header('Access-Control-Allow-Origin: ' . $origin);
            header('Vary: Origin');
        }
        header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS');
        header('Access-Control-Allow-Headers: Authorization, Content-Type, Accept-Language');
        header('Access-Control-Max-Age: 600');
        header('X-Content-Type-Options: nosniff');
        header('Cache-Control: no-store');
    }
}
