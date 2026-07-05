package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.port.OutboxEventPublisher;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Outbox 패턴의 릴레이 스케줄러.
 *
 * <p>5초 간격으로 {@code PENDING} 상태의 OutboxEvent를 최대 100건씩 조회하여
 * Kafka에 발행하고, 성공 시 {@code PUBLISHED}, 실패 시 retryCount를 증가시킨다.
 * retryCount가 임계값에 도달하면 {@link OutboxEvent#incrementRetryCount()}가
 * 내부적으로 상태를 {@code FAILED}로 전환한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxPublishScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublishScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final OutboxTransactionalWorker worker;

    @Value("${app.outbox.publish.batch-size}")
    private int publishBatchSize;

    @Value("${app.outbox.replay.batch-size}")
    private int replayBatchSize;

    /**
     * PENDING 이벤트를 조회해 Kafka로 발행한다.
     *
     * <p>메서드 자체에는 트랜잭션을 두지 않는다. 조회와 발행 결과 반영은 각각
     * {@link OutboxTransactionalWorker}의 짧은 트랜잭션으로 위임하고, Kafka 발행은
     * 트랜잭션 밖에서 수행하여 외부 I/O 지연이 DB 커넥션·락 점유로 전파되지 않도록 한다.
     */
    @Scheduled(fixedDelayString = "${app.outbox.publish.interval-ms}")
    public void publishPendingEvents() {
        List<OutboxEvent> pending = worker.loadPending(publishBatchSize);
        for (OutboxEvent event : pending) {
            boolean success;
            try {
                outboxEventPublisher.publish(event);
                success = true;
            } catch (Exception e) {
                LOG.warn("[Outbox] 발행 실패 eventId={}", event.getId(), e);
                success = false;
            }
            worker.markResult(event.getId(), success);
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.monitor.interval-ms}")
    public void monitorFailedEvents() {
        long failedCount = outboxEventRepository.countByStatus(OutboxStatus.FAILED);
        long deadCount = outboxEventRepository.countByStatus(OutboxStatus.DEAD);
        if (failedCount > 0) {
            LOG.warn("[Outbox] FAILED 이벤트 {}건 감지. replay 필요 여부 확인 요망.", failedCount);
        }
        if (deadCount > 0) {
            LOG.warn("[Outbox] DEAD 이벤트 {}건 감지. 수동 확인 및 조치 필요.", deadCount);
        }
    }

    @Scheduled(fixedDelayString = "${app.outbox.replay.interval-ms}")
    @Transactional
    public void replayFailedEvents() {
        List<OutboxEvent> failed = outboxEventRepository.findByStatusOrderByCreatedAt(
                OutboxStatus.FAILED, replayBatchSize);
        for (OutboxEvent event : failed) {
            event.resetToPending();
            outboxEventRepository.save(event);
        }
        LOG.info("[Outbox] FAILED → PENDING 재전환 {}건 완료.", failed.size());
    }
}
