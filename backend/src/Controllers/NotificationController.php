<?php

declare(strict_types=1);

namespace ChemManager\Api\Controllers;

use ChemManager\Api\Core\Request;
use ChemManager\Api\Core\Response;
use ChemManager\Api\Http\App;

/**
 * Notification centre of the application header: unread badge, popup list, mark as read.
 * Visible to every signed-in user, so no module permission is required.
 */
final class NotificationController
{
    public function __construct(private readonly App $app)
    {
    }

    public function index(Request $request): Response
    {
        $userId = (int) $request->user['id'];
        $limit = $request->queryInt('limit', 20);
        $unreadOnly = in_array($request->queryString('unreadOnly'), ['1', 'true', 'yes'], true);

        return Response::ok([
            'items' => $this->app->notifications->latest($userId, $limit, $unreadOnly),
            'unreadCount' => $this->app->notifications->unreadCount($userId),
        ]);
    }

    public function markRead(Request $request): Response
    {
        $userId = (int) $request->user['id'];
        $ids = $request->body['ids'] ?? null;
        if (!is_array($ids) || $ids === []) {
            return Response::error(422, 'VALIDATION_FAILED', 'The submitted data is invalid.',
                ['ids' => 'validation.required']);
        }
        $updated = $this->app->notifications->markRead($userId, $ids);

        return Response::ok(['updated' => $updated, 'unreadCount' => $this->app->notifications->unreadCount($userId)]);
    }

    public function markAllRead(Request $request): Response
    {
        $updated = $this->app->notifications->markAllRead((int) $request->user['id']);

        return Response::ok(['updated' => $updated, 'unreadCount' => 0]);
    }
}
