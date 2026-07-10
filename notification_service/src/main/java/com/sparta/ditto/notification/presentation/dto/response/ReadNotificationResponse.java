package com.sparta.ditto.notification.presentation.dto.response;

import com.sparta.ditto.notification.application.dto.ReadNotificationResult;

public record ReadNotificationResponse(
        String notificationId,
        boolean isRead
) {
    public static ReadNotificationResponse from(ReadNotificationResult result) {
        return new ReadNotificationResponse(
                result.notificationId().toString(),
                result.isRead()
        );
    }
}
