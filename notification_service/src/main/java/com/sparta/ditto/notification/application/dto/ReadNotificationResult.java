package com.sparta.ditto.notification.application.dto;

import com.sparta.ditto.notification.domain.entity.Notification;
import java.util.UUID;

public record ReadNotificationResult(
        UUID notificationId,
        boolean isRead
) {
    public static ReadNotificationResult from(Notification notification) {
        return new ReadNotificationResult(notification.getId(), notification.isRead());
    }
}