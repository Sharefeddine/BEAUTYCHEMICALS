<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

/**
 * Regex router with {placeholder} segments. Each route carries the permission it requires
 * (null = authenticated only, false = anonymous).
 */
final class Router
{
    /** @var array<int,array{methods:array<int,string>,regex:string,params:array<int,string>,handler:callable,permission:string|null,auth:bool}> */
    private array $routes = [];

    /**
     * @param string|array<int,string> $methods
     * @param string|null $permission required permission, null for "any authenticated user"
     * @param bool $auth false for public endpoints
     */
    public function add(string|array $methods, string $pattern, callable $handler,
                        ?string $permission = null, bool $auth = true): void
    {
        $placeholders = [];
        $regex = preg_replace_callback('/\{(\w+)\}/', static function (array $matches) use (&$placeholders): string {
            $placeholders[] = $matches[1];

            return '([^/]+)';
        }, $pattern);

        $this->routes[] = [
            'methods' => array_map('strtoupper', (array) $methods),
            'regex' => '#^' . $regex . '$#',
            'params' => $placeholders,
            'handler' => $handler,
            'permission' => $permission,
            'auth' => $auth,
        ];
    }

    /**
     * @return array{handler:callable,permission:string|null,auth:bool,params:array<string,string>}
     */
    public function match(string $method, string $path): array
    {
        $allowed = [];
        foreach ($this->routes as $route) {
            if (!preg_match($route['regex'], $path, $matches)) {
                continue;
            }
            if (!in_array($method, $route['methods'], true)) {
                $allowed = array_merge($allowed, $route['methods']);
                continue;
            }
            $params = [];
            foreach ($route['params'] as $index => $name) {
                $params[$name] = rawurldecode($matches[$index + 1]);
            }

            return [
                'handler' => $route['handler'],
                'permission' => $route['permission'],
                'auth' => $route['auth'],
                'params' => $params,
            ];
        }

        if ($allowed !== []) {
            throw ApiException::methodNotAllowed(array_values(array_unique($allowed)));
        }

        throw new ApiException(404, 'ROUTE_NOT_FOUND', 'No endpoint matches ' . $method . ' ' . $path . '.');
    }

    public function count(): int
    {
        return count($this->routes);
    }
}
