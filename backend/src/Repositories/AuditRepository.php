<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\Database;

/**
 * Read side of the audit trail (the trail itself is append-only).
 */
final class AuditRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    /** @return array{rows:array<int,array>,total:int} */
    public function paginate(int $page, int $pageSize, string $module, string $action, string $search): array
    {
        $where = ['1 = 1'];
        $params = [];

        if ($module !== '') {
            $where[] = 'module = :module';
            $params['module'] = strtoupper($module);
        }
        if ($action !== '') {
            $where[] = 'action = :action';
            $params['action'] = strtoupper($action);
        }
        if ($search !== '') {
            $where[] = '(username LIKE :searchUsername OR entity LIKE :searchEntity OR reference LIKE :searchReference)';
            $params['searchUsername'] = '%' . $search . '%';
            $params['searchEntity'] = '%' . $search . '%';
            $params['searchReference'] = '%' . $search . '%';
        }

        $clause = 'WHERE ' . implode(' AND ', $where);
        $total = (int) $this->db->scalar('SELECT COUNT(*) FROM auditLogs ' . $clause, $params);
        $rows = $this->db->select(
            'SELECT id, userId, username, action, module, entity, entityId, reference, severity, ipAddress,
                    payload, occurredAt
             FROM auditLogs ' . $clause . '
             ORDER BY occurredAt DESC, id DESC
             LIMIT :limit OFFSET :offset',
            $params + ['limit' => $pageSize, 'offset' => ($page - 1) * $pageSize]
        );

        return ['rows' => $rows, 'total' => $total];
    }
}
