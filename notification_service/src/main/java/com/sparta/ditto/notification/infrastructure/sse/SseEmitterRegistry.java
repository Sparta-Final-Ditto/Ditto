package com.sparta.ditto.notification.infrastructure.sse;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 사용자별 SSE 연결(emitter) 레지스트리. 사용자당 다중 emitter(멀티 탭·디바이스)를
 * 허용하며, 값 리스트는 스레드 세이프(CopyOnWriteArrayList)로 두어 전송 중 안전한 순회·제거를 보장한다.
 * emitter가 0개가 되면 Map 키 자체를 제거한다.
 */
@Component
public class SseEmitterRegistry {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void add(UUID userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public List<SseEmitter> get(UUID userId) {
        return emitters.getOrDefault(userId, List.of());
    }

    public void remove(UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (key, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    public Map<UUID, List<SseEmitter>> asMap() {
        return emitters;
    }

    public int connectionCount() {
        return emitters.size();
    }
}