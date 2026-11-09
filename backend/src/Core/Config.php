<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

use RuntimeException;

/**
 * Configuration precedence: environment variables > backend/config/config.php > defaults,
 * so CI and containers never need a configuration file on disk.
 */
final class Config
{
    private function __construct(private readonly array $values)
    {
    }

    public static function load(?string $path = null): self
    {
        $file = $path ?? dirname(__DIR__, 2) . '/config/config.php';
        $stored = is_file($file) ? require $file : [];
        if (!is_array($stored)) {
            throw new RuntimeException('backend/config/config.php must return an array');
        }

        $env = static function (string $key, $default) {
            $value = getenv($key);

            return $value === false ? $default : $value;
        };

        return new self([
            'database' => [
                'host' => $env('CHEMMANAGER_DB_HOST', $stored['database']['host'] ?? '127.0.0.1'),
                'port' => (int) $env('CHEMMANAGER_DB_PORT', $stored['database']['port'] ?? 3306),
                'name' => $env('CHEMMANAGER_DB_NAME', $stored['database']['name'] ?? 'chemmanager'),
                'user' => $env('CHEMMANAGER_DB_USER', $stored['database']['user'] ?? 'chemapp'),
                'password' => $env('CHEMMANAGER_DB_PASSWORD', $stored['database']['password'] ?? ''),
            ],
            'security' => [
                'tokenTtlHours' => (int) $env('CHEMMANAGER_TOKEN_TTL', $stored['security']['tokenTtlHours'] ?? 8),
                'maxLoginAttempts' => (int) $env('CHEMMANAGER_MAX_ATTEMPTS', $stored['security']['maxLoginAttempts'] ?? 5),
                'lockoutMinutes' => (int) $env('CHEMMANAGER_LOCKOUT', $stored['security']['lockoutMinutes'] ?? 2),
                'bcryptCost' => (int) $env('CHEMMANAGER_BCRYPT_COST', $stored['security']['bcryptCost'] ?? 12),
            ],
            'app' => [
                'env' => $env('CHEMMANAGER_ENV', $stored['app']['env'] ?? 'production'),
                'debug' => filter_var($env('CHEMMANAGER_DEBUG', $stored['app']['debug'] ?? false), FILTER_VALIDATE_BOOL),
                'timezone' => $stored['app']['timezone'] ?? 'Africa/Algiers',
                'logFile' => $env('CHEMMANAGER_LOG', $stored['app']['logFile'] ?? dirname(__DIR__, 2) . '/var/api.log'),
                'allowedOrigins' => $stored['app']['allowedOrigins'] ?? [],
            ],
        ]);
    }

    public function get(string $key, $default = null)
    {
        $cursor = $this->values;
        foreach (explode('.', $key) as $segment) {
            if (!is_array($cursor) || !array_key_exists($segment, $cursor)) {
                return $default;
            }
            $cursor = $cursor[$segment];
        }

        return $cursor;
    }

    public function isDebug(): bool
    {
        return (bool) $this->get('app.debug', false);
    }
}
