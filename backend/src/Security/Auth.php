<?php

declare(strict_types=1);

namespace ChemManager\Api\Security;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Audit;
use ChemManager\Api\Core\Config;
use ChemManager\Api\Core\Database;
use ChemManager\Api\Core\Request;

/**
 * Password authentication and opaque bearer tokens.
 *
 * The token is a 32-byte random value returned once to the client; only its sha256 is stored in
 * apiTokens.tokenHash, so a database dump never yields a usable session.
 */
final class Auth
{
    public function __construct(
        private readonly Database $db,
        private readonly Config $config,
        private readonly Audit $audit
    ) {
    }

    /**
     * @return array{token:string,expiresAt:string,user:array,permissions:array<int,string>}
     */
    public function login(string $username, string $password, string $ip, string $userAgent): array
    {
        $user = $this->db->first(
            'SELECT id, username, passwordHash, fullName, email, jobTitle, isActive, mustChangePassword,
                    lockedUntil, failedAttempts, deletedAt
             FROM users WHERE username = :username',
            ['username' => $username]
        );

        // Same answer for an unknown account and a wrong password: no account enumeration.
        if ($user === null || $user['deletedAt'] !== null) {
            $this->audit->log(null, 'LOGIN', 'AUTH', 'users', null,
                ['username' => $username, 'reason' => 'unknown_user'], 'WARNING', $ip);
            throw ApiException::unauthorized('Invalid username or password.');
        }

        if ((int) $user['isActive'] !== 1) {
            throw ApiException::forbidden('This account is disabled.');
        }

        if ($user['lockedUntil'] !== null && strtotime((string) $user['lockedUntil']) > time()) {
            $remaining = strtotime((string) $user['lockedUntil']) - time();
            throw ApiException::tooManyRequests('Too many failed attempts, the account is temporarily locked.',
                max(1, $remaining));
        }

        if (!password_verify($password, (string) $user['passwordHash'])) {
            $this->registerFailure($user, $ip);

            throw ApiException::unauthorized('Invalid username or password.');
        }

        // Transparent re-hash when the cost factor of the stored hash is below the configured one.
        if (password_needs_rehash((string) $user['passwordHash'], PASSWORD_BCRYPT,
            ['cost' => (int) $this->config->get('security.bcryptCost')])) {
            $this->db->execute('UPDATE users SET passwordHash = :hash WHERE id = :id', [
                'hash' => password_hash($password, PASSWORD_BCRYPT, ['cost' => (int) $this->config->get('security.bcryptCost')]),
                'id' => (int) $user['id'],
            ]);
        }

        $this->db->execute(
            'UPDATE users SET failedAttempts = 0, lockedUntil = NULL, lastLoginAt = NOW(), lastLoginIp = :ip
             WHERE id = :id',
            ['ip' => $ip, 'id' => (int) $user['id']]
        );

        $tokenData = $this->issueToken((int) $user['id'], $ip, $userAgent);
        $permissions = $this->permissionsFor((int) $user['id']);

        $this->audit->log($user, 'LOGIN', 'AUTH', 'users', (int) $user['id'], [], 'INFO', $ip);

        return [
            'token' => $tokenData['token'],
            'expiresAt' => $tokenData['expiresAt'],
            'user' => $this->publicUser($user + ['lastLoginAt' => gmdate('Y-m-d H:i:s')]),
            'permissions' => $permissions,
        ];
    }

    /**
     * Resolves the bearer token of the request into a user + permissions.
     *
     * @return array{user:array,permissions:array<int,string>,tokenId:int}
     */
    public function authenticate(Request $request): array
    {
        $token = $request->bearerToken();
        if ($token === null || strlen($token) < 32) {
            throw ApiException::unauthorized();
        }

        $row = $this->db->first(
            'SELECT t.id AS tokenId, t.expiresAt, u.id, u.username, u.fullName, u.email, u.jobTitle,
                    u.isActive, u.mustChangePassword, u.lastLoginAt, u.deletedAt
             FROM apiTokens t
             JOIN users u ON u.id = t.userId
             WHERE t.tokenHash = :hash AND t.revokedAt IS NULL AND t.expiresAt > NOW()',
            ['hash' => hash('sha256', $token)]
        );

        if ($row === null) {
            throw ApiException::unauthorized('The session has expired, please sign in again.');
        }
        if ((int) $row['isActive'] !== 1 || $row['deletedAt'] !== null) {
            throw ApiException::forbidden('This account is disabled.');
        }

        $permissions = $this->permissionsFor((int) $row['id']);

        return [
            'user' => $this->publicUser($row),
            'permissions' => $permissions,
            'tokenId' => (int) $row['tokenId'],
        ];
    }

    public function logout(string $token, ?array $user): void
    {
        $this->db->execute(
            'UPDATE apiTokens SET revokedAt = NOW() WHERE tokenHash = :hash AND revokedAt IS NULL',
            ['hash' => hash('sha256', $token)]
        );
        $this->audit->log($user, 'LOGOUT', 'AUTH', 'apiTokens', isset($user['id']) ? (int) $user['id'] : null);
    }

    public function changePassword(array $user, string $currentPassword, string $newPassword, string $ip): void
    {
        $row = $this->db->first('SELECT id, passwordHash FROM users WHERE id = :id', ['id' => (int) $user['id']]);
        if ($row === null) {
            throw ApiException::notFound('User');
        }
        if (!password_verify($currentPassword, (string) $row['passwordHash'])) {
            throw ApiException::validation(['currentPassword' => 'validation.password_mismatch']);
        }
        if (strlen($newPassword) < 8 || !preg_match('/[A-Za-z]/', $newPassword) || !preg_match('/\d/', $newPassword)) {
            throw ApiException::validation(['newPassword' => 'validation.password_weak']);
        }

        $this->db->transaction(function () use ($user, $newPassword, $ip): void {
            $this->db->execute(
                'UPDATE users SET passwordHash = :hash, mustChangePassword = 0, passwordChangedAt = NOW(),
                        failedAttempts = 0, lockedUntil = NULL WHERE id = :id',
                [
                    'hash' => password_hash($newPassword, PASSWORD_BCRYPT,
                        ['cost' => (int) $this->config->get('security.bcryptCost')]),
                    'id' => (int) $user['id'],
                ]
            );
            // every other session of this user is invalidated
            $this->db->execute('UPDATE apiTokens SET revokedAt = NOW() WHERE userId = :id AND revokedAt IS NULL',
                ['id' => (int) $user['id']]);
            $this->audit->log($user, 'UPDATE', 'USERS', 'users', (int) $user['id'],
                ['field' => 'password'], 'WARNING', $ip);
        });
    }

    /** @return array<int,string> */
    public function permissionsFor(int $userId): array
    {
        $rows = $this->db->select(
            'SELECT DISTINCT p.code
             FROM permissions p
             JOIN rolePermissions rp ON rp.permissionId = p.id
             JOIN userRoles ur ON ur.roleId = rp.roleId
             JOIN roles r ON r.id = ur.roleId AND r.deletedAt IS NULL
             WHERE ur.userId = :userId
             ORDER BY p.code',
            ['userId' => $userId]
        );

        return array_map(static fn (array $row): string => (string) $row['code'], $rows);
    }

    /** @return array<int,string> */
    public function rolesFor(int $userId): array
    {
        $rows = $this->db->select(
            'SELECT r.code FROM roles r
             JOIN userRoles ur ON ur.roleId = r.id
             WHERE ur.userId = :userId AND r.deletedAt IS NULL
             ORDER BY r.code',
            ['userId' => $userId]
        );

        return array_map(static fn (array $row): string => (string) $row['code'], $rows);
    }

    /** @return array{token:string,expiresAt:string} */
    private function issueToken(int $userId, string $ip, string $userAgent): array
    {
        $token = bin2hex(random_bytes(32));
        $hours = max(1, (int) $this->config->get('security.tokenTtlHours'));
        $expiresAt = (new \DateTimeImmutable('+' . $hours . ' hours'))->format('Y-m-d H:i:s');

        $this->db->execute(
            'INSERT INTO apiTokens (userId, tokenHash, expiresAt, ipAddress, userAgent)
             VALUES (:userId, :hash, :expiresAt, :ip, :agent)',
            [
                'userId' => $userId,
                'hash' => hash('sha256', $token),
                'expiresAt' => $expiresAt,
                'ip' => $ip,
                'agent' => $userAgent !== '' ? $userAgent : null,
            ]
        );

        return ['token' => $token, 'expiresAt' => $expiresAt];
    }

    private function registerFailure(array $user, string $ip): void
    {
        $attempts = (int) $user['failedAttempts'] + 1;
        $max = max(1, (int) $this->config->get('security.maxLoginAttempts'));

        if ($attempts >= $max) {
            $lockUntil = (new \DateTimeImmutable('+' . max(1, (int) $this->config->get('security.lockoutMinutes'))
                . ' minutes'))->format('Y-m-d H:i:s');
            $this->db->execute('UPDATE users SET failedAttempts = :attempts, lockedUntil = :lockUntil WHERE id = :id', [
                'attempts' => $attempts,
                'lockUntil' => $lockUntil,
                'id' => (int) $user['id'],
            ]);
            $this->audit->log($user, 'LOGIN', 'AUTH', 'users', (int) $user['id'],
                ['reason' => 'locked', 'attempts' => $attempts], 'CRITICAL', $ip);

            return;
        }

        $this->db->execute('UPDATE users SET failedAttempts = :attempts WHERE id = :id', [
            'attempts' => $attempts,
            'id' => (int) $user['id'],
        ]);
        $this->audit->log($user, 'LOGIN', 'AUTH', 'users', (int) $user['id'],
            ['reason' => 'bad_password', 'attempts' => $attempts], 'WARNING', $ip);
    }

    private function publicUser(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'username' => (string) $row['username'],
            'fullName' => (string) $row['fullName'],
            'email' => (string) $row['email'],
            'phone' => (string) ($row['phone'] ?? ''),
            'jobTitle' => (string) ($row['jobTitle'] ?? ''),
            'roleCodes' => $this->rolesFor((int) $row['id']),
            'isActive' => (int) ($row['isActive'] ?? 1) === 1,
            'mustChangePassword' => (int) ($row['mustChangePassword'] ?? 0) === 1,
            'lastLoginAt' => $row['lastLoginAt'] ?? null,
        ];
    }
}
