package com.chemmanager.service.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.model.NotificationItem;
import com.chemmanager.service.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Notification centre served by {@code /api/notifications}.
 *
 * <p>The badge count is the one the server computed for the signed-in user; the list is the same
 * rows the popup shows, so both always agree.</p>
 */
public final class ApiNotificationService implements NotificationService {

    private final ApiClient client;

    public ApiNotificationService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<NotificationItem> latest(int limit) {
        JsonNode payload = client.get(ApiEndpoints.NOTIFICATIONS,
                Map.of("limit", Integer.toString(limit <= 0 ? 20 : limit)), JsonNode.class);
        List<NotificationItem> items = new ArrayList<>();
        if (payload != null) {
            for (JsonNode row : payload.path("items")) {
                items.add(ApiMapper.notification(row));
            }
        }
        return items;
    }

    @Override
    public int unreadCount() {
        JsonNode payload = client.get(ApiEndpoints.NOTIFICATIONS, Map.of("limit", "1"), JsonNode.class);
        return payload == null ? 0 : payload.path("unreadCount").asInt(0);
    }

    @Override
    public void markRead(long id) {
        client.postVoid(ApiEndpoints.NOTIFICATIONS_READ, Map.of("ids", List.of(id)));
    }

    @Override
    public void markAllRead() {
        client.postVoid(ApiEndpoints.NOTIFICATIONS_READ_ALL, null);
    }
}
