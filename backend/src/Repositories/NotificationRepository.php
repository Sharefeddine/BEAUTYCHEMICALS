<?php

declare(strict_types=1);

namespace ChemManager\Api\Repositories;

use ChemManager\Api\Core\Database;

/**
 * Header notification centre.
 *
 * Rows with {@code userId = NULL} are broadcast information; only the rows owned by the user can
 * be marked as read, because {@code notifications.isRead} is a per-row column. A broadcast becomes
 * dismissable as soon as the API fans it out per user (Phase 8 notification rules).
 */
final class NotificationRepository
{
    public function __construct(private readonly Database $db)
    {
    }

    /** @return array<int,array> */
    public function latest(int $userId, int $limit, bool $unreadOnly): array
    {
        $sql = 'SELECT id, userId, titleKey, message, severity, linkModule, linkId, isRead, createdAt
                FROM notifications
                WHERE (userId = :userIdOwned OR userId IS NULL)';
        if ($unreadOnly) {
            $sql .= ' AND isRead = 0';
        }
        $sql .= ' ORDER BY createdAt DESC, id DESC LIMIT :limit';

        $rows = $this->db->select($sql, [
            'userIdOwned' => $userId,
            'limit' => min(200, max(1, $limit)),
        ]);

        return array_map(static fn (array $row): array => [
            'id' => (int) $row['id'],
            'titleKey' => (string) $row['titleKey'],
            'message' => (string) $row['message'],
            'severity' => (string) $row['severity'],
            'moduleId' => $row['linkModule'] !== null ? (string) $row['linkModule'] : null,
            'linkId' => $row['linkId'] !== null ? (int) $row['linkId'] : null,
            'read' => (int) $row['isRead'] === 1,
            'broadcast' => $row['userId'] === null,
            'createdAt' => $row['createdAt'],
        ], $rows);
    }

    public function unreadCount(int $userId): int
    {
        return (int) $this->db->scalar(
            'SELECT COUNT(*) FROM notifications WHERE (userId = :userIdOwned OR userId IS NULL) AND isRead = 0',
            ['userIdOwned' => $userId]
        );
    }

    /** @param array<int,int|string> $ids */
    public function markRead(int $userId, array $ids): int
    {
        $clean = array_values(array_filter(array_map('intval', $ids), static fn (int $id): bool => $id > 0));
        if ($clean === []) {
            return 0;
        }
        $placeholders = [];
        $params = ['userIdOwned' => $userId];
        foreach ($clean as $index => $id) {
            $placeholders[] = ':id' . $index;
            $params['id' . $index] = $id;
        }

        return $this->db->execute(
            'UPDATE notifications SET isRead = 1, readAt = NOW()
             WHERE userId = :userIdOwned AND isRead = 0 AND id IN (' . implode(', ', $placeholders) . ')',
            $params
        );
    }

    public function markAllRead(int $userId): int
    {
        return $this->db->execute(
            'UPDATE notifications SET isRead = 1, readAt = NOW() WHERE userId = :userIdOwned AND isRead = 0',
            ['userIdOwned' => $userId]
        );
    }
}
