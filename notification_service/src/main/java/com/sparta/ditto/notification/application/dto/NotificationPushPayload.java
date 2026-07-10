package com.sparta.ditto.notification.application.dto;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * SSE 실시간 전송 payload. 알림 목록 item과 동일 구조에서 roomUnreadCount만 제외한다.
 * 모두 저장 시점에 이미 존재하는 값이므로 재조회·재계산 없이 그대로 전송한다.
 * metaData는 JSON 문자열 그대로 전달하며 파싱/재직렬화하지 않는다.
 */
public record NotificationPushPayload(
        UUID notificationId,
        NotificationType type,
        String message,
        boolean isRead,
        String metaData,
        Instant createdAt
) {
    public static NotificationPushPayload from(Notification notification) {
        return new NotificationPushPayload(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getMetaData(),
                notification.getCreatedAt()
        );
    }
}
