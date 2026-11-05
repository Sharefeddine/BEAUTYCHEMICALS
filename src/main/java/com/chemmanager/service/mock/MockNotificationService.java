package com.chemmanager.service.mock;

import java.util.List;

import com.chemmanager.model.NotificationItem;
import com.chemmanager.repository.MockDataStore;
import com.chemmanager.service.NotificationService;

/**
 * Notification center over the demo dataset. Entries are rebuilt from the current state
 * (stock levels, batch expiry, unpaid balances) each time they are requested, and the read
 * flag is kept in memory so marking as read behaves like the real backend.
 */
public final class MockNotificationService implements NotificationService {

    private final MockDataStore store = MockDataStore.get();
    private final java.util.Set<Long> readIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public List<NotificationItem> latest(int limit) {
        List<NotificationItem> items = store.notifications();
        items.forEach(item -> {
            if (readIds.contains(item.id())) {
                item.markRead();
            }
        });
        return limit <= 0 || items.size() <= limit ? items : items.subList(0, limit);
    }

    @Override
    public int unreadCount() {
        return (int) latest(0).stream().filter(item -> !item.isRead()).count();
    }

    @Override
    public void markRead(long id) {
        readIds.add(id);
    }

    @Override
    public void markAllRead() {
        latest(0).forEach(item -> readIds.add(item.id()));
    }
}
