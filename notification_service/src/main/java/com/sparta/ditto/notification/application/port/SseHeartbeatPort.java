package com.sparta.ditto.notification.application.port;

/**
 * heartbeat 브로드캐스트 아웃고잉 포트. 전체 SSE 연결에 heartbeat를 전송하고 죽은 연결을
 * 정리하는 구현은 infrastructure/sse 어댑터가 담당한다. Application은 SSE 기술을 알지 못한다.
 */
public interface SseHeartbeatPort {

    void broadcastHeartbeat();
}
