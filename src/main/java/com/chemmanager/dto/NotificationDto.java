package com.chemmanager.dto;

import java.time.Instant;

/** Wire format of a notification-center entry. */
public record NotificationDto(long id, String category, String severity, String title, String message,
                              Instant createdAt, String moduleId, boolean read) {
}
