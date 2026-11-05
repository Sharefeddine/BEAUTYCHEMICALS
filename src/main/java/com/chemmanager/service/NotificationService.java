package com.chemmanager.service;

import java.util.List;

import com.chemmanager.model.NotificationItem;

/** In-app notification center. */
public interface NotificationService {

    List<NotificationItem> latest(int limit);

    int unreadCount();

    void markRead(long id);

    void markAllRead();
}
