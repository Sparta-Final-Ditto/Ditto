package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 릴레이의 DB 트랜잭션 경계를 담당하는 워커.
 *
 * <p>Kafka 발행(외부 I/O)을 트랜잭션 밖으로 분리하기 위해, 조회와 상태 반영을
 * 각각 짧은 개별 트랜잭션으로 수행한다. {@link OutboxPublishScheduler}와 별도 빈으로
 * 분리한 이유는 같은 클래스 내부 호출이 Spring AOP 프록시를 우회하여
 * {@link Transactional}이 무시되는 self-invocation 문제를 피하기 위함이다.
 */
@Component
@RequiredArgsConstructor
public class OutboxTransactionalWorker {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * PENDING 이벤트를 조회한다. 짧은 트랜잭션으로 조회만 수행하고 즉시 커밋하여
     * 비관적 락 점유 시간을 최소화한다. 반환된 엔티티는 detached 상태이다.
     */
    @Transactional
    public List<OutboxEvent> loadPending(int batchSize) {
        return outboxEventRepository.findPendingForUpdate(OutboxStatus.PENDING, batchSize);
    }

    /**
     * 트랜잭션 밖에서 수행된 Kafka 발행 결과를 건별 짧은 트랜잭션으로 반영한다.
     * 성공 시 PUBLISHED, 실패 시 retryCount를 증가시킨다.
     */
    @Transactional
    public void markResult(UUID eventId, boolean success) {
        outboxEventRepository.findById(eventId).ifPresent(event -> {
            if (success) {
                event.markPublished();
            } else {
                event.incrementRetryCount();
            }
            outboxEventRepository.save(event);
        });
    }
}
