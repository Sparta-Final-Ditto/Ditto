package com.sparta.ditto.notification.infrastructure.config;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DisplayName("NotificationDeadLetterRecoverer - DataIntegrityViolationException은 DLT 미발행, 그 외는 DLT 발행")
class NotificationDeadLetterRecovererTest {

    private final DeadLetterPublishingRecoverer delegate = mock(DeadLetterPublishingRecoverer.class);
    private final NotificationDeadLetterRecoverer recoverer =
            new NotificationDeadLetterRecoverer(delegate);

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("post-events", 0, 0L, "key", "value");
    }

    @Test
    @DisplayName("DataIntegrityViolationException 입력 → DLT 발행 생략, 예외 없이 정상 종료")
    void dataIntegrityViolation_skipsDltPublishing() {
        Exception ex = new DataIntegrityViolationException("duplicate key");

        assertThatCode(() -> recoverer.accept(record(), ex)).doesNotThrowAnyException();

        verify(delegate, never()).accept(any(), any());
    }

    @Test
    @DisplayName("원인 체인에 DataIntegrityViolationException이 감싸여 있어도 DLT 발행 생략")
    void wrappedDataIntegrityViolation_skipsDltPublishing() {
        Exception ex = new RuntimeException("listener failed",
                new DataIntegrityViolationException("unique violation"));

        recoverer.accept(record(), ex);

        verify(delegate, never()).accept(any(), any());
    }

    @Test
    @DisplayName("그 외 예외(JsonProcessingException 등)는 DLT 발행 경로(delegate)로 진행")
    void otherException_publishesToDlt() {
        Exception ex = new JsonParseException(null, "not valid json");

        recoverer.accept(record(), ex);

        verify(delegate, times(1)).accept(any(), any());
    }

    @Test
    @DisplayName("DLT 발행 자체가 실패해도(브로커 장애) 예외를 전파하지 않고 정상 리턴(유실 허용 skip)")
    void dltPublishFailure_isSwallowedAndReturnsNormally() {
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(delegate).accept(any(), any());
        Exception ex = new JsonParseException(null, "not valid json"); // DLT 발행 경로 진입

        assertThatCode(() -> recoverer.accept(record(), ex)).doesNotThrowAnyException();

        verify(delegate, times(1)).accept(any(), any()); // 발행을 시도는 했음
    }

    @Test
    @DisplayName("예외 분기 판별: DIVE(직접/래핑)만 true, 그 외는 false")
    void isIdempotentSuccessSkip_classifiesCauseChain() {
        assertThat(NotificationDeadLetterRecoverer.isIdempotentSuccessSkip(
                new DataIntegrityViolationException("x"))).isTrue();
        assertThat(NotificationDeadLetterRecoverer.isIdempotentSuccessSkip(
                new RuntimeException("wrap", new DataIntegrityViolationException("x")))).isTrue();
        assertThat(NotificationDeadLetterRecoverer.isIdempotentSuccessSkip(
                new IllegalArgumentException("contract"))).isFalse();
    }
}