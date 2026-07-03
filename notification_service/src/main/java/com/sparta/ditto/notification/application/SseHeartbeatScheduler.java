package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.port.SseHeartbeatPort;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기적으로 heartbeat를 전체 SSE 연결에 브로드캐스트하여 연결을 유지하고 죽은 연결을 청소한다.
 * 실제 전송/정리는 SseHeartbeatPort 구현(어댑터)에 위임하며, 이 클래스는 주기 조율만 담당한다.
 */
@Component
@RequiredArgsConstructor
public class SseHeartbeatScheduler {

    private final SseHeartbeatPort sseHeartbeatPort;

    @Scheduled(fixedDelayString = "${app.sse.heartbeat-interval-ms:30000}")
    public void sendHeartbeat() {
        sseHeartbeatPort.broadcastHeartbeat();
    }
}