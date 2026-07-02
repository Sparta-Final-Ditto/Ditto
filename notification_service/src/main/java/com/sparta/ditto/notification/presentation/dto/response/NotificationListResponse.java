package com.sparta.ditto.notification.presentation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationListResponse(
        long unreadCount,
        List<NotificationItemResponse> notifications,
        String nextCursor,
        boolean hasNext
) {
    public static NotificationListResponse from(NotificationListResult result) {
        return new NotificationListResponse(
                result.unreadCount(),
                result.notifications().stream()
                        .map(NotificationItemResponse::from)
                        .toList(),
                result.nextCursor() != null ? result.nextCursor().toString() : null,
                result.hasNext()
        );
    }
}