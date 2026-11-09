<?php

declare(strict_types=1);

namespace ChemManager\Api\Core;

/**
 * Append-only audit trail. Every mutating endpoint and every authentication event writes one row.
 */
final class Audit
{
    public function __construct(private readonly Database $db)
    {
    }

    public function log(
        ?array $user,
        string $action,
        string $module,
        string $entity = '',
        ?int $entityId = null,
        array $payload = [],
        string $severity = 'INFO',
        string $ip = '',
        string $reference = ''
    ): void {
        $this->db->execute(
            'INSERT INTO auditLogs (userId, username, action, module, entity, entityId, reference, severity, ipAddress, payload)
             VALUES (:userId, :username, :action, :module, :entity, :entityId, :reference, :severity, :ip, :payload)',
            [
                'userId' => isset($user['id']) ? (int) $user['id'] : null,
                'username' => (string) ($user['username'] ?? ''),
                'action' => strtoupper($action),
                'module' => strtoupper($module),
                'entity' => $entity,
                'entityId' => $entityId,
                'reference' => $reference,
                'severity' => $severity,
                'ip' => $ip !== '' ? $ip : null,
                'payload' => $payload === [] ? null
                    : (string) json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            ]
        );
    }
}
