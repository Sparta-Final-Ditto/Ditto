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

    @Value("${outbox.publish.batch-size}")
    private int publishBatchSize;

    @Value("${outbox.replay.batch-size}")
    private int replayBatchSize;

    @Scheduled(fixedDelayString = "${outbox.publish.interval-ms}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingForUpdate(
                OutboxStatus.PENDING, publishBatchSize);
        for (OutboxEvent event : pending) {
            try {
                outboxEventPublisher.publish(event);
                event.markPublished();
            } catch (Exception e) {
                event.incrementRetryCount();
            }
            outboxEventRepository.save(event);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.monitor.interval-ms}")
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

    @Scheduled(fixedDelayString = "${outbox.replay.interval-ms}")
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
