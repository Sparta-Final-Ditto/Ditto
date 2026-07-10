package com.sparta.ditto.notification.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ditto.notification.application.dto.NotificationItemResult;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationItemResponse(
        String notificationId,
        NotificationType type,
        String actorId,
        TargetType targetType,
        String targetId,
        String message,
        boolean isRead,
        String metaData,
        Instant createdAt,
        Long roomUnreadCount
) {
    public static NotificationItemResponse from(NotificationItemResult result) {
        return new NotificationItemResponse(
                result.notificationId().toString(),
                result.type(),
                result.actorId() != null ? result.actorId().toString() : null,
                result.targetType(),
                result.targetId(),
                result.message(),
                result.isRead(),
                result.metaData(),
                result.createdAt(),
                result.roomUnreadCount()
        );
    }
}
