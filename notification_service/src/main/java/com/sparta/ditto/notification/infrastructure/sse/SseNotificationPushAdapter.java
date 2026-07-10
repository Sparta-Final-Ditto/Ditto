package com.sparta.ditto.notification.infrastructure.sse;

import com.sparta.ditto.notification.application.dto.NotificationPushPayload;
import com.sparta.ditto.notification.application.port.NotificationPushPort;
import com.sparta.ditto.notification.application.port.SseHeartbeatPort;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * NotificationPushPort 구현. SseEmitterRegistry를 통해 실제 SseEmitter.send를 수행한다.
 * 전송 실패(IOException, 완료된 emitter의 IllegalStateException)는 해당 emitter만
 * 레지스트리에서 제거하며, 같은 사용자의 다른 emitter는 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseNotificationPushAdapter implements NotificationPushPort, SseHeartbeatPort {

    private static final String NOTIFICATION_EVENT = "notification";
    private static final String HEARTBEAT_EVENT = "heartbeat";
    private static final String HEARTBEAT_DATA = "ping";

    private final SseEmitterRegistry registry;

    @Override
    public void push(UUID receiverId, NotificationPushPayload payload) {
        for (SseEmitter emitter : registry.get(receiverId)) {
            sendOrEvict(receiverId, emitter, NOTIFICATION_EVENT, payload);
        }
    }

    /** heartbeat를 전체 연결에 전송하며, 전송 실패한 죽은 연결을 청소한다. */
    @Override
    public void broadcastHeartbeat() {
        for (Map.Entry<UUID, List<SseEmitter>> entry : registry.asMap().entrySet()) {
            UUID userId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                sendOrEvict(userId, emitter, HEARTBEAT_EVENT, HEARTBEAT_DATA);
            }
        }
    }

    private void sendOrEvict(UUID userId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            registry.remove(userId, emitter);
            log.debug("SSE emitter 전송 실패로 제거 - userId={}, event={}", userId, eventName, e);
        }
    }
}
