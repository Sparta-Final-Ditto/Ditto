package com.sparta.ditto.notification.application.dto;

import java.util.List;
import java.util.UUID;

public record NotificationListResult(
        long unreadCount,
        List<NotificationItemResult> notifications,
        UUID nextCursor,
        boolean hasNext
) {
    public static NotificationListResult of(
            long unreadCount,
            List<NotificationItemResult> notifications,
            UUID nextCursor,
            boolean hasNext) {
        return new NotificationListResult(unreadCount, notifications, nextCursor, hasNext);
    }
}
