<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\ApiException;
use ChemManager\Api\Core\Database;

/**
 * Accounts, their roles and their permission checkboxes.
 */
final class UserRepository
{
    private const SORTABLE = ['username', 'fullName', 'email', 'createdAt', 'lastLoginAt'];

    public function __construct(private readonly Database $db, private readonly int $bcryptCost = 12)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $search, string $sort, string $direction,
                             ?bool $activeOnly = null): array
    {
        $sort = in_array($sort, self::SORTABLE, true) ? $sort : 'username';
        $direction = strtoupper($direction) === 'DESC' ? 'DESC' : 'ASC';
        $where = ['u.deletedAt IS NULL'];
        $params = [];

        if ($search !== '') {
            $where[] = '(u.username LIKE :search OR u.fullName LIKE :search OR u.email LIKE :search)';
            $params['search'] = '%' . $search . '%';
        }
        if ($activeOnly === true) {
            $where[] = 'u.isActive = 1';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM users u ' . $clause, $params);

        $rows = $this->db->select(
            'SELECT u.id, u.username, u.fullName, u.email, u.phone, u.jobTitle, u.isActive,
                    u.lastLoginAt, u.mustChangePassword, d.nameFr AS departmentName,
                    GROUP_CONCAT(DISTINCT r.code ORDER BY r.code SEPARATOR \',\') AS roleCodes
             FROM users u
             LEFT JOIN departments d ON d.id = u.departmentId
             LEFT JOIN userRoles ur ON ur.userId = u.id
             LEFT JOIN roles r ON r.id = ur.roleId AND r.deletedAt IS NULL
             ' . $clause . '
             GROUP BY u.id, u.username, u.fullName, u.email, u.phone, u.jobTitle, u.isActive,
                      u.lastLoginAt, u.mustChangePassword, d.nameFr
             ORDER BY u.' . $sort . ' ' . $direction . '
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => array_map([$this, 'hydrate'], $rows), 'total' => $total];
    }

    public function find(int $id): array
    {
        $row = $this->db->first(
            'SELECT u.id, u.username, u.fullName, u.email, u.phone, u.jobTitle, u.isActive, u.departmentId,
                    u.lastLoginAt, u.mustChangePassword, u.createdAt
             FROM users u WHERE u.id = :id AND u.deletedAt IS NULL',
            ['id' => $id]
        );
        if ($row === null) {
            throw ApiException::notFound('User');
        }
        $user = $this->hydrate($row);
        $user['roles'] = $this->roleIds($id);

        return $user;
    }

    public function create(array $data, ?int $actorId): array
    {
        $this->db->execute(
            'INSERT INTO users (username, passwordHash, fullName, email, phone, jobTitle, departmentId,
                                isActive, mustChangePassword, createdBy)
             VALUES (:username, :hash, :fullName, :email, :phone, :jobTitle, :departmentId, :isActive, 1, :createdBy)',
            [
                'username' => $data['username'],
                'hash' => password_hash($data['password'], PASSWORD_BCRYPT, ['cost' => $this->bcryptCost]),
                'fullName' => $data['fullName'],
                'email' => $data['email'],
                'phone' => $data['phone'] ?? '',
                'jobTitle' => $data['jobTitle'] ?? '',
                'departmentId' => $data['departmentId'] ?? null,
                'isActive' => (int) ($data['isActive'] ?? true),
                'createdBy' => $actorId,
            ]
        );
        $id = $this->db->lastId();
        if (!empty($data['roleIds'])) {
            $this->syncRoles($id, $data['roleIds']);
        }

        return $this->find($id);
    }

    public function update(int $id, array $data, ?int $actorId): array
    {
        $current = $this->find($id);
        $this->db->execute(
            'UPDATE users SET fullName = :fullName, email = :email, phone = :phone, jobTitle = :jobTitle,
                    departmentId = :departmentId, isActive = :isActive, updatedBy = :updatedBy
             WHERE id = :id',
            [
                'fullName' => $data['fullName'] ?? $current['fullName'],
                'email' => $data['email'] ?? $current['email'],
                'phone' => $data['phone'] ?? '',
                'jobTitle' => $data['jobTitle'] ?? '',
                'departmentId' => $data['departmentId'] ?? null,
                'isActive' => (int) ($data['isActive'] ?? $current['isActive']),
                'updatedBy' => $actorId,
                'id' => $id,
            ]
        );
        if (array_key_exists('roleIds', $data)) {
            $this->syncRoles($id, (array) $data['roleIds']);
        }

        return $this->find($id);
    }

    public function softDelete(int $id, ?int $actorId): void
    {
        $this->find($id);
        $this->db->execute(
            'UPDATE users SET deletedAt = NOW(), isActive = 0, updatedBy = :updatedBy WHERE id = :id',
            ['updatedBy' => $actorId, 'id' => $id]
        );
        $this->db->execute('UPDATE apiTokens SET revokedAt = NOW() WHERE userId = :id AND revokedAt IS NULL',
            ['id' => $id]);
    }

    public function resetPassword(int $id, string $password): void
    {
        $this->find($id);
        $this->db->execute(
            'UPDATE users SET passwordHash = :hash, mustChangePassword = 1, failedAttempts = 0, lockedUntil = NULL
             WHERE id = :id',
            ['hash' => password_hash($password, PASSWORD_BCRYPT, ['cost' => $this->bcryptCost]), 'id' => $id]
        );
        $this->db->execute('UPDATE apiTokens SET revokedAt = NOW() WHERE userId = :id AND revokedAt IS NULL',
            ['id' => $id]);
    }

    /** @return array<int,int> */
    public function roleIds(int $id): array
    {
        $rows = $this->db->select('SELECT roleId FROM userRoles WHERE userId = :id ORDER BY roleId',
            ['id' => $id]);

        return array_map(static fn (array $row): int => (int) $row['roleId'], $rows);
    }

    /** @param array<int,int|string> $roleIds */
    public function syncRoles(int $userId, array $roleIds): void
    {
        $ids = array_values(array_unique(array_map('intval', $roleIds)));
        $this->db->transaction(function () use ($userId, $ids): void {
            $this->db->execute('DELETE FROM userRoles WHERE userId = :id', ['id' => $userId]);
            foreach ($ids as $roleId) {
                if ($roleId <= 0) {
                    continue;
                }
                $this->db->execute('INSERT IGNORE INTO userRoles (userId, roleId) VALUES (:userId, :roleId)',
                    ['userId' => $userId, 'roleId' => $roleId]);
            }
        });
    }

    /** @return array<int,array> */
    public function roles(): array
    {
        return $this->db->select(
            'SELECT r.id, r.code, r.nameFr, r.nameAr, r.nameEn, r.descriptionFr, r.isSystem,
                    COUNT(DISTINCT ur.userId) AS userCount,
                    COUNT(DISTINCT rp.permissionId) AS permissionCount
             FROM roles r
             LEFT JOIN userRoles ur ON ur.roleId = r.id
             LEFT JOIN rolePermissions rp ON rp.roleId = r.id
             WHERE r.deletedAt IS NULL
             GROUP BY r.id, r.code, r.nameFr, r.nameAr, r.nameEn, r.descriptionFr, r.isSystem
             ORDER BY r.code'
        );
    }

    /** @return array<int,array> */
    public function permissions(): array
    {
        return $this->db->select(
            'SELECT id, code, module, labelFr, labelAr, labelEn, sortOrder FROM permissions ORDER BY sortOrder'
        );
    }

    /** @return array<int,array> */
    public function departments(): array
    {
        return $this->db->select(
            'SELECT id, code, nameFr, nameAr, nameEn FROM departments WHERE deletedAt IS NULL ORDER BY nameFr'
        );
    }

    /** @return array<int,int> */
    public function rolePermissionIds(int $roleId): array
    {
        $rows = $this->db->select('SELECT permissionId FROM rolePermissions WHERE roleId = :id', ['id' => $roleId]);

        return array_map(static fn (array $row): int => (int) $row['permissionId'], $rows);
    }

    /** @param array<int,int|string> $permissionIds */
    public function syncRolePermissions(int $roleId, array $permissionIds): void
    {
        $role = $this->db->first('SELECT id, isSystem FROM roles WHERE id = :id AND deletedAt IS NULL',
            ['id' => $roleId]);
        if ($role === null) {
            throw ApiException::notFound('Role');
        }
        if ((int) $role['isSystem'] === 1) {
            throw ApiException::conflict('System roles keep their permission matrix.', 'ROLE_LOCKED');
        }
        $ids = array_values(array_unique(array_map('intval', $permissionIds)));
        $this->db->transaction(function () use ($roleId, $ids): void {
            $this->db->execute('DELETE FROM rolePermissions WHERE roleId = :id', ['id' => $roleId]);
            foreach ($ids as $permissionId) {
                if ($permissionId <= 0) {
                    continue;
                }
                $this->db->execute(
                    'INSERT IGNORE INTO rolePermissions (roleId, permissionId) VALUES (:roleId, :permissionId)',
                    ['roleId' => $roleId, 'permissionId' => $permissionId]
                );
            }
        });
    }

    private function hydrate(array $row): array
    {
        return [
            'id' => (int) $row['id'],
            'username' => (string) $row['username'],
            'fullName' => (string) $row['fullName'],
            'email' => (string) $row['email'],
            'phone' => (string) ($row['phone'] ?? ''),
            'jobTitle' => (string) ($row['jobTitle'] ?? ''),
            'departmentId' => isset($row['departmentId']) ? (int) $row['departmentId'] : null,
            'departmentName' => $row['departmentName'] ?? null,
            'isActive' => (int) $row['isActive'] === 1,
            'mustChangePassword' => (int) ($row['mustChangePassword'] ?? 0) === 1,
            'lastLoginAt' => $row['lastLoginAt'] ?? null,
            'createdAt' => $row['createdAt'] ?? null,
            'roleCodes' => isset($row['roleCodes']) && $row['roleCodes'] !== null
                ? explode(',', (string) $row['roleCodes'])
                : [],
        ];
    }
}
