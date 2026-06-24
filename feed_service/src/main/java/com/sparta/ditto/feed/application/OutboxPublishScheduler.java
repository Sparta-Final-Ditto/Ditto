package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.port.OutboxEventPublisher;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * PENDING 이벤트를 조회하여 순차 발행한다.
     *
     * <p>발행 성공: {@link OutboxEvent#markPublished()} 호출 후 저장.
     * 발행 실패: {@link OutboxEvent#incrementRetryCount()} 호출 후 저장.
     * 이전 실행이 끝난 시점으로부터 5초 후 다음 실행이 시작된다({@code fixedDelay}).
     */
    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAt(
                OutboxStatus.PENDING, 100);
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
}
