package com.sparta.ditto.notification.application.event;

import com.sparta.ditto.notification.application.dto.NotificationPushPayload;
import java.util.UUID;

/**
 * 알림 저장 트랜잭션 내부에서 발행되는 도메인 이벤트. AFTER_COMMIT 리스너가 수신해
 * receiverId로 라우팅하여 NotificationPushPort로 실시간 전송한다(리스너는 다음 단계).
 * receiverId는 전송 대상(연결 조회 키), payload는 전송 본문이다.
 */
public record NotificationCreatedEvent(
        UUID receiverId,
        NotificationPushPayload payload
) {
}