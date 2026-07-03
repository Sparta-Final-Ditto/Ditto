package com.sparta.ditto.notification.application.port;

import com.sparta.ditto.notification.application.dto.NotificationPushPayload;
import java.util.UUID;

/**
 * 실시간 알림 전송 아웃고잉 포트. Application은 SSE 기술을 알지 못하며,
 * infrastructure/sse 어댑터가 emitter 레지스트리를 소유하고 구현한다(구현체는 다음 단계).
 */
public interface NotificationPushPort {

    void push(UUID receiverId, NotificationPushPayload payload);
}