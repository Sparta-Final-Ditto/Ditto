package com.sparta.ditto.notification.infrastructure.sse;

import com.sparta.ditto.notification.application.dto.NotificationPushPayload;
import com.sparta.ditto.notification.domain.type.NotificationType;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("SseNotificationPushAdapter - 실시간 전송/heartbeat 브로드캐스트 (실제 Registry)")
class SseNotificationPushAdapterTest {

    private SseEmitterRegistry registry;
    private SseNotificationPushAdapter adapter;

    private final UUID userId = UUID.randomUUID();
    private final NotificationPushPayload payload = new NotificationPushPayload(
            UUID.randomUUID(), NotificationType.LIKE, "새 알림", false,
            "{\"postId\":\"post_1\"}", Instant.now());

    @BeforeEach
    void setUp() {
        registry = new SseEmitterRegistry();
        adapter = new SseNotificationPushAdapter(registry);
    }

    // ── push ────────────────────────────────────────────────────────

    @Test
    @DisplayName("push 시 해당 사용자의 모든 emitter(멀티 탭)로 event명 notification + payload를 전송한다")
    void push_sendsToAllEmittersOfUser() throws IOException {
        SseEmitter tab1 = mock(SseEmitter.class);
        SseEmitter tab2 = mock(SseEmitter.class);
        registry.add(userId, tab1);
        registry.add(userId, tab2);

        adapter.push(userId, payload);

        assertEventSent(tab1, "notification", payload);
        assertEventSent(tab2, "notification", payload);
    }

    static Stream<Arguments> sendFailures() {
        return Stream.of(
                Arguments.of("IOException", new IOException("broken pipe")),
                Arguments.of("IllegalStateException", new IllegalStateException("completed"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sendFailures")
    @DisplayName("send가 IOException/IllegalStateException을 던진 emitter만 제거되고 나머지는 전송을 지속한다")
    void push_removesOnlyFailingEmitter(String label, Throwable sendFailure) throws IOException {
        SseEmitter healthy = mock(SseEmitter.class);
        SseEmitter broken = mock(SseEmitter.class);
        doThrow(sendFailure).when(broken).send(any(SseEventBuilder.class));
        registry.add(userId, healthy);
        registry.add(userId, broken);

        adapter.push(userId, payload);

        verify(healthy, times(1)).send(any(SseEventBuilder.class));
        assertThat(registry.get(userId)).containsExactly(healthy);  // broken 제거, healthy 유지
    }

    @Test
    @DisplayName("연결이 없는 사용자에게 push하면 예외 없이 no-op이다")
    void push_unknownUser_isNoOp() {
        assertThatCode(() -> adapter.push(UUID.randomUUID(), payload))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("사용자의 emitter가 0개가 되면 레지스트리 Map에서 키 자체가 제거된다")
    void push_lastEmitterRemoved_dropsUserKey() throws IOException {
        SseEmitter only = mock(SseEmitter.class);
        doThrow(new IOException()).when(only).send(any(SseEventBuilder.class));
        registry.add(userId, only);

        adapter.push(userId, payload);

        assertThat(registry.get(userId)).isEmpty();
        assertThat(registry.connectionCount()).isZero();
    }

    // ── heartbeat 브로드캐스트 ────────────────────────────────────────

    @Test
    @DisplayName("heartbeat 브로드캐스트 시 전체 연결에 event명 heartbeat/data ping을 전송한다")
    void broadcastHeartbeat_sendsToAllConnections() throws IOException {
        UUID otherUser = UUID.randomUUID();
        SseEmitter a = mock(SseEmitter.class);
        SseEmitter b = mock(SseEmitter.class);
        SseEmitter c = mock(SseEmitter.class);
        registry.add(userId, a);
        registry.add(userId, b);
        registry.add(otherUser, c);

        adapter.broadcastHeartbeat();

        assertEventSent(a, "heartbeat", "ping");
        assertEventSent(b, "heartbeat", "ping");
        assertEventSent(c, "heartbeat", "ping");
    }

    @Test
    @DisplayName("heartbeat 전송 실패한 emitter(죽은 연결)는 제거된다")
    void broadcastHeartbeat_removesDeadEmitter() throws IOException {
        SseEmitter healthy = mock(SseEmitter.class);
        SseEmitter dead = mock(SseEmitter.class);
        doThrow(new IOException()).when(dead).send(any(SseEventBuilder.class));
        registry.add(userId, healthy);
        registry.add(userId, dead);

        adapter.broadcastHeartbeat();

        assertThat(registry.get(userId)).containsExactly(healthy);
    }

    // ── helper: 캡처한 SseEventBuilder에서 event명/데이터를 검증 ────────────────

    private static void assertEventSent(SseEmitter emitter, String eventName, Object expectedData)
            throws IOException {
        ArgumentCaptor<SseEventBuilder> captor = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        List<Object> data = captor.getValue().build().stream()
                .map(DataWithMediaType::getData)
                .toList();
        assertThat(data).anySatisfy(d -> assertThat(d.toString()).contains("event:" + eventName));
        assertThat(data).contains(expectedData);
    }
}