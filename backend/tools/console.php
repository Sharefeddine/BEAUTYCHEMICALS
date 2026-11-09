<?php

declare(strict_types=1);

/**
 * Command line companion of the API: installation checks, password resets, token purge.
 * Usage: php backend/tools/console.php <command> [arguments]
 */

require dirname(__DIR__) . '/src/autoload.php';

use ChemManager\Api\Core\Config;
use ChemManager\Api\Core\Database;

$config = Config::load();
$db = new Database($config);

$command = $argv[1] ?? 'help';

$out = static function (string $line): void {
    echo $line . PHP_EOL;
};

switch ($command) {
    case 'db:check':
        $tables = (int) $db->scalar(
            'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()'
        );
        $permissions = (int) $db->scalar('SELECT COUNT(*) FROM permissions');
        $roles = (int) $db->scalar('SELECT COUNT(*) FROM roles WHERE deletedAt IS NULL');
        $users = (int) $db->scalar('SELECT COUNT(*) FROM users WHERE deletedAt IS NULL');
        $out(sprintf('tables=%d permissions=%d roles=%d users=%d', $tables, $permissions, $roles, $users));
        $missing = [];
        foreach (['companies', 'users', 'products', 'rawMaterials', 'formulations', 'productionOrders',
                     'invoices', 'stockMovements', 'settings', 'auditLogs'] as $table) {
            if ((int) $db->scalar(
                'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = :name',
                ['name' => $table]
            ) === 0) {
                $missing[] = $table;
            }
        }
        if ($missing !== []) {
            $out('MISSING TABLES: ' . implode(', ', $missing));
            exit(1);
        }
        if ($permissions !== 45 || $roles !== 12) {
            $out('UNEXPECTED SEED COUNT: expected 45 permissions and 12 roles, run database/seed/*.sql');
            exit(1);
        }
        $out('database ok');
        break;

    case 'user:password':
        $username = $argv[2] ?? '';
        $password = $argv[3] ?? '';
        if ($username === '' || $password === '') {
            $out('usage: console.php user:password <username> <password>');
            exit(2);
        }
        if (strlen($password) < 8 || !preg_match('/[A-Za-z]/', $password) || !preg_match('/\d/', $password)) {
            $out('the password must be at least 8 characters and mix letters and digits');
            exit(2);
        }
        $user = $db->first('SELECT id FROM users WHERE username = :username AND deletedAt IS NULL',
            ['username' => $username]);
        if ($user === null) {
            $out('unknown user: ' . $username);
            exit(1);
        }
        $db->execute(
            'UPDATE users SET passwordHash = :hash, mustChangePassword = 0, passwordChangedAt = NOW(),
                    failedAttempts = 0, lockedUntil = NULL WHERE id = :id',
            [
                'hash' => password_hash($password, PASSWORD_BCRYPT,
                    ['cost' => (int) $config->get('security.bcryptCost')]),
                'id' => (int) $user['id'],
            ]
        );
        $db->execute('UPDATE apiTokens SET revokedAt = NOW() WHERE userId = :id AND revokedAt IS NULL',
            ['id' => (int) $user['id']]);
        $out('password updated for ' . $username);
        break;

    case 'token:purge':
        $deleted = $db->execute(
            'DELETE FROM apiTokens WHERE expiresAt < NOW() - INTERVAL 7 DAY OR (revokedAt IS NOT NULL AND revokedAt < NOW() - INTERVAL 7 DAY)'
        );
        $out('purged tokens: ' . $deleted);
        break;

    case 'role:list':
        foreach ($db->select('SELECT code, nameFr, (SELECT COUNT(*) FROM rolePermissions rp WHERE rp.roleId = r.id) AS permissions
                              FROM roles r WHERE deletedAt IS NULL ORDER BY code') as $role) {
            $out(sprintf('%-22s %-32s %d permissions', $role['code'], $role['nameFr'], $role['permissions']));
        }
        break;

    case 'install':
        $out('1. create the database: CREATE DATABASE chemmanager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;');
        $out('2. apply database/schema/*.sql then database/seed/*.sql (see database/README.md)');
        $out('3. copy backend/config/config.example.php to backend/config/config.php and edit it');
        $out('4. php backend/tools/console.php db:check');
        $out('5. php backend/tools/console.php user:password admin <new-password>');
        break;

    default:
        $out('ChemManager API console');
        $out('  db:check                               verify the connection and the seed counts');
        $out('  user:password <username> <password>    set a password and revoke the sessions');
        $out('  token:purge                            delete the expired bearer tokens');
        $out('  role:list                              print the roles and their permission count');
        $out('  install                                print the installation steps');
        break;
}
