package com.chemmanager.service.api;

import java.util.List;
import java.util.Map;

import com.chemmanager.api.ApiClient;
import com.chemmanager.api.ApiEndpoints;
import com.chemmanager.dto.NotificationDto;
import com.chemmanager.model.NotificationItem;
import com.chemmanager.model.Severity;
import com.chemmanager.service.NotificationService;
import com.fasterxml.jackson.core.type.TypeReference;

/** Notification center served by {@code /notifications}. */
public final class ApiNotificationService implements NotificationService {

    private final ApiClient client;

    public ApiNotificationService(ApiClient client) {
        this.client = client;
    }

    @Override
    public List<NotificationItem> latest(int limit) {
        Map<String, String> query = limit > 0 ? Map.of("limit", Integer.toString(limit)) : Map.of();
        List<NotificationDto> dtos = client.get(ApiEndpoints.NOTIFICATIONS, query, new TypeReference<List<NotificationDto>>() {
        });
        return dtos.stream().map(ApiNotificationService::toModel).toList();
    }

    @Override
    public int unreadCount() {
        return latest(0).stream().filter(item -> !item.isRead()).toList().size();
    }

    @Override
    public void markRead(long id) {
        client.postVoid(ApiEndpoints.NOTIFICATIONS_READ, Map.of("ids", List.of(id)));
    }

    @Override
    public void markAllRead() {
        client.postVoid(ApiEndpoints.NOTIFICATIONS_READ, Map.of("all", true));
    }

    private static NotificationItem toModel(NotificationDto dto) {
        NotificationItem.Category category;
        try {
            category = NotificationItem.Category.valueOf(dto.category() == null ? "SYSTEM" : dto.category());
        } catch (IllegalArgumentException ex) {
            category = NotificationItem.Category.SYSTEM;
        }
        Severity severity;
        try {
            severity = Severity.valueOf(dto.severity() == null ? "INFO" : dto.severity());
        } catch (IllegalArgumentException ex) {
            severity = Severity.INFO;
        }
        return new NotificationItem(dto.id(), category, severity, dto.title(), dto.message(),
                dto.createdAt(), dto.moduleId(), dto.read());
    }
}
