<?php

declare(strict_types=1);

namespace ChemManager\Api\Security;

use ChemManager\Api\Core\ApiException;

/**
 * Permission checks are recomputed from the database on every request (never trusted from the
 * token), and the SUPER_ADMIN wildcard is the only bypass.
 */
final class Authorizer
{
    public const WILDCARD_ROLE = 'SUPER_ADMIN';

    /**
     * @param string|null $permission permission required by the route (null = any signed-in user)
     * @param array<int,string> $permissions permissions of the current user
     * @param array<int,string> $roles role codes of the current user
     */
    public function assert(?string $permission, array $permissions, array $roles): void
    {
        if ($permission === null) {
            return;
        }
        if (in_array($permission, $permissions, true)) {
            return;
        }
        if (in_array(self::WILDCARD_ROLE, $roles, true)) {
            return;
        }

        throw ApiException::forbidden('Missing permission: ' . $permission . '.');
    }
}
