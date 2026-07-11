package com.sparta.ditto.notification.application.dto;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.time.Instant;
import java.util.UUID;

public record NotificationItemResult(
        UUID notificationId,
        NotificationType type,
        UUID actorId,
        TargetType targetType,
        String targetId,
        String message,
        boolean isRead,
        String metaData,
        Instant createdAt,
        Long roomUnreadCount
) {
    public static NotificationItemResult of(Notification notification, Long roomUnreadCount) {
        return new NotificationItemResult(
                notification.getId(),
                notification.getType(),
                notification.getActorId(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getMetaData(),
                notification.getCreatedAt(),
                roomUnreadCount
        );
    }
}
