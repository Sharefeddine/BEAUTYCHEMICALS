package com.chemmanager.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A notification-center entry. Titles and messages are resolved text (the API localises them
 * server side, the mock service resolves i18n keys), while {@code moduleId} allows deep-linking
 * to the screen concerned by the alert.
 */
public final class NotificationItem {

    /** Business domain of the notification, drives the icon and colour. */
    public enum Category {
        STOCK("stock"),
        EXPIRY("expiry"),
        PRODUCTION("production"),
        QUALITY("quality"),
        PAYMENT("payment"),
        DELIVERY("delivery"),
        SAFETY("safety"),
        SYSTEM("system");

        private final String iconKey;

        Category(String iconKey) {
            this.iconKey = iconKey;
        }

        public String iconKey() {
            return iconKey;
        }
    }

    private final long id;
    private final Category category;
    private final Severity severity;
    private final String title;
    private final String message;
    private final Instant createdAt;
    private final String moduleId;
    private boolean read;

    public NotificationItem(long id, Category category, Severity severity, String title, String message,
                            Instant createdAt, String moduleId, boolean read) {
        this.id = id;
        this.category = Objects.requireNonNullElse(category, Category.SYSTEM);
        this.severity = Objects.requireNonNullElse(severity, Severity.INFO);
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        this.createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        this.moduleId = moduleId;
        this.read = read;
    }

    public long id() {
        return id;
    }

    public Category category() {
        return category;
    }

    public Severity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String message() {
        return message;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String moduleId() {
        return moduleId;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }
}
