package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.port.OutboxEventPublisher;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private OutboxTransactionalWorker worker;

    @InjectMocks
    private OutboxPublishScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "replayBatchSize", 100);
    }

    private OutboxEvent pendingEvent() {
        return new OutboxEvent("post-events", "POST_LIKED", UUID.randomUUID(), "{}");
    }

    @Test
    @DisplayName("replayFailedEvents - FAILED 이벤트를 PENDING으로 재전환 후 저장")
    void replayFailedEvents_FAILED_to_PENDING() {
        // given
        OutboxEvent failedEvent = pendingEvent();
        failedEvent.incrementRetryCount();
        failedEvent.incrementRetryCount();
        failedEvent.incrementRetryCount();
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);

        when(outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.FAILED, 100))
                .thenReturn(List.of(failedEvent));

        // when
        scheduler.replayFailedEvents();

        // then
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(failedEvent.getRetryCount()).isZero();
        assertThat(failedEvent.getFailedAt()).isNull();
        assertThat(failedEvent.getReplayCount()).isEqualTo(1);
        verify(outboxEventRepository).save(failedEvent);
    }

    @Test
    @DisplayName("replayFailedEvents - DEAD 이벤트가 포함되어도 상태 변화 없이 저장됨")
    void replayFailedEvents_DEAD_이벤트_상태_유지() {
        // given - DEAD 상태 이벤트 생성 (3 replay cycle 소진 후 4번째 실패)
        OutboxEvent deadEvent = pendingEvent();
        for (int i = 0; i < 3; i++) {
            deadEvent.incrementRetryCount();
            deadEvent.incrementRetryCount();
            deadEvent.incrementRetryCount();
            deadEvent.resetToPending();
        }
        deadEvent.incrementRetryCount();
        deadEvent.incrementRetryCount();
        deadEvent.incrementRetryCount();
        assertThat(deadEvent.getStatus()).isEqualTo(OutboxStatus.DEAD);

        when(outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.FAILED, 100))
                .thenReturn(List.of(deadEvent));

        // when
        scheduler.replayFailedEvents();

        // then - resetToPending은 DEAD에서 no-op이므로 여전히 DEAD
        assertThat(deadEvent.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(deadEvent.getReplayCount()).isEqualTo(3);
        verify(outboxEventRepository).save(deadEvent);
    }
}
