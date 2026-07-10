package com.sparta.ditto.notification.application.port;

import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결 등록/해제 아웃고잉 포트. SseService와 emitter 레지스트리 어댑터 간의 계약이다.
 * 등록/제거 대상 식별을 위해 SseEmitter를 시그니처에 노출하나, Application은 레지스트리
 * 구현(infrastructure)을 알지 못한다.
 */
public interface SseConnectionPort {

    void add(UUID userId, SseEmitter emitter);

    void remove(UUID userId, SseEmitter emitter);
}
