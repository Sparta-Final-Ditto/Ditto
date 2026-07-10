package com.sparta.ditto.notification.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;

/**
 * 재시도 소진/not-retryable 실패 레코드를 DLT로 발행하되, {@link DataIntegrityViolationException}
 * (멱등 성공 = 이미 처리된 정상 상황)만은 DLT로 보내지 않고 로그 후 정상 종료한다(TRD 10장).
 *
 * <p>PostgreSQL은 제약 위반 시 트랜잭션이 abort되어 사전 exists 체크(1차 방어)를 뚫은 레이스가
 * 여기까지 오면 DLT가 아니라 "이미 처리됨"으로 간주하는 것이 옳다. 예외 타입 분기를 단위 테스트할 수
 * 있도록 {@link #isIdempotentSuccessSkip(Throwable)}로 분리한다.
 */
@Slf4j
public class NotificationDeadLetterRecoverer implements ConsumerRecordRecoverer {

    private final DeadLetterPublishingRecoverer delegate;

    public NotificationDeadLetterRecoverer(DeadLetterPublishingRecoverer delegate) {
        this.delegate = delegate;
    }

    /** 예외 또는 그 원인 체인에 DataIntegrityViolationException이 있으면 멱등 성공으로 간주한다. */
    public static boolean isIdempotentSuccessSkip(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof DataIntegrityViolationException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        if (isIdempotentSuccessSkip(exception)) {
            log.info("멱등 성공 skip — DLT 미발행, 정상 종료: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            return;
        }
        // 예외 객체를 마지막 인자로 넘겨 스택트레이스를 보존한다(getMessage()만 남기지 않는다).
        log.error("재시도 소진/not-retryable → DLT 발행: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset(), exception);
        try {
            delegate.accept(record, exception);
        } catch (Exception dltPublishFailure) {
            // DLT 발행 자체 실패(브로커 장애 등) → 유실 허용 fallback: 로그 후 정상 종료(skip)한다(TRD 10장).
            log.error("DLT 발행 실패 — 유실 허용 skip: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), dltPublishFailure);
        }
    }
}
