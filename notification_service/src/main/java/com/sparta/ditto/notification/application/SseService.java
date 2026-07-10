package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.port.SseConnectionPort;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결 생성 유스케이스. connect가 SseEmitter를 생성·등록하고 반환한다.
 * SseEmitter는 HTTP 상세이나 연결 생성의 프레임워크 계약상 격리가 불가능하므로,
 * Application 계층에서 이 클래스(및 연결 포트)에 한해 예외적으로 사용을 허용한다.
 */
@Slf4j
@Service
public class SseService {

    private final SseConnectionPort connectionPort;
    private final long timeoutMs;

    public SseService(
            SseConnectionPort connectionPort,
            @Value("${app.sse.timeout-ms:1800000}") long timeoutMs) {
        this.connectionPort = connectionPort;
        this.timeoutMs = timeoutMs;
    }

    public SseEmitter connect(UUID userId) {
        SseEmitter emitter = createEmitter(timeoutMs);
        emitter.onCompletion(() -> connectionPort.remove(userId, emitter));
        emitter.onTimeout(() -> connectionPort.remove(userId, emitter));
        emitter.onError(error -> connectionPort.remove(userId, emitter));

        connectionPort.add(userId, emitter);
        sendInitialHeartbeat(userId, emitter);
        return emitter;
    }

    protected SseEmitter createEmitter(long timeout) {
        return new SseEmitter(timeout);
    }

    private void sendInitialHeartbeat(UUID userId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("ping"));
        } catch (IOException | IllegalStateException e) {
            connectionPort.remove(userId, emitter);
            log.debug("SSE 초기 heartbeat 전송 실패로 연결 제거 - userId={}", userId, e);
        }
    }
}
