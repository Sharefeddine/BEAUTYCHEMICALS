<?php

declare(strict_types=1);

/**
 * Copy to config/config.php and adapt. Never commit the real file (see .gitignore).
 * Every value can also be provided through an environment variable, which is what CI does.
 */
return [
    'database' => [
        'host' => '127.0.0.1',
        'port' => 3306,
        'name' => 'chemmanager',
        // dedicated account: SELECT, INSERT, UPDATE only - no DELETE, no DDL
        'user' => 'chemapp',
        'password' => 'change-me',
    ],
    'security' => [
        'tokenTtlHours' => 8,
        'maxLoginAttempts' => 5,
        'lockoutMinutes' => 2,
        'bcryptCost' => 12,
    ],
    'app' => [
        'env' => 'production',
        'debug' => false,
        'timezone' => 'Africa/Algiers',
        'logFile' => dirname(__DIR__) . '/var/api.log',
        // empty = the desktop client only (no Origin header); list domains for a web client
        'allowedOrigins' => [],
    ],
];
