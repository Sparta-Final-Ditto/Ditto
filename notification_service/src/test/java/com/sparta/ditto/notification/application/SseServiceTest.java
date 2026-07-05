package com.sparta.ditto.notification.application;

import com.sparta.ditto.notification.application.port.SseConnectionPort;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SseService - 연결 생성/등록/초기 heartbeat/정리 콜백")
class SseServiceTest {

    private static final long TIMEOUT_MS = 1_800_000L;

    @Mock
    private SseConnectionPort connectionPort;

    private SseService sseService;
    private SseEmitter emitter;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // createEmitter 시드(생성 seam)만 spy로 대체해 mock emitter를 주입한다.
        sseService = spy(new SseService(connectionPort, TIMEOUT_MS));
        emitter = mock(SseEmitter.class);
        doReturn(emitter).when(sseService).createEmitter(anyLong());
    }

    @Test
    @DisplayName("connect 시 emitter를 레지스트리에 등록하고 초기 heartbeat 1회를 즉시 전송한다")
    void connect_registersEmitterAndSendsInitialHeartbeat() throws IOException {
        // When
        SseEmitter result = sseService.connect(userId);

        // Then: 반환 + 등록
        assertThat(result).isSameAs(emitter);
        verify(connectionPort).add(userId, emitter);

        // 초기 heartbeat: event명 heartbeat / data ping 1회
        ArgumentCaptor<SseEventBuilder> captor = ArgumentCaptor.forClass(SseEventBuilder.class);
        verify(emitter).send(captor.capture());
        List<Object> data = captor.getValue().build().stream()
                .map(DataWithMediaType::getData)
                .toList();
        assertThat(data).anySatisfy(d -> assertThat(d.toString()).contains("event:heartbeat"));
        assertThat(data).contains("ping");
    }

    @Test
    @DisplayName("onCompletion/onTimeout/onError 콜백이 발동하면 해당 emitter를 레지스트리에서 제거한다")
    void connect_lifecycleCallbacks_removeEmitter() {
        // When
        sseService.connect(userId);

        // Then: 세 콜백을 캡처해 각각 발동시키면 remove가 호출된다
        ArgumentCaptor<Runnable> onCompletion = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Runnable> onTimeout = ArgumentCaptor.forClass(Runnable.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<Throwable>> onError = ArgumentCaptor.forClass(Consumer.class);

        verify(emitter).onCompletion(onCompletion.capture());
        verify(emitter).onTimeout(onTimeout.capture());
        verify(emitter).onError(onError.capture());

        onCompletion.getValue().run();
        onTimeout.getValue().run();
        onError.getValue().accept(new RuntimeException("boom"));

        verify(connectionPort, times(3)).remove(userId, emitter);
    }
}