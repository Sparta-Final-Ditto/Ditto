package com.sparta.ditto.notification.infrastructure.sse;

import com.sparta.ditto.notification.application.event.NotificationCreatedEvent;
import com.sparta.ditto.notification.application.port.NotificationPushPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 저장 커밋 완료 후 실시간 전송을 트리거한다. 저장 트랜잭션과 분리하기 위해 AFTER_COMMIT
 * 시점에 전용 executor에서 비동기로 push한다. 전송 실패는 알림 저장에 영향을 주지 않으므로
 * 내부에서 삼키고 warn 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final NotificationPushPort notificationPushPort;

    @Async("ssePushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            notificationPushPort.push(event.receiverId(), event.payload());
        } catch (RuntimeException e) {
            log.warn("SSE 실시간 전송 실패 - receiverId={}", event.receiverId(), e);
        }
    }
}
