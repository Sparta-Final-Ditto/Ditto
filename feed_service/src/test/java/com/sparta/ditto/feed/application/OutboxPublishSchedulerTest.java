package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublishSchedulerTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublishScheduler scheduler;

    private OutboxEvent pendingEvent() {
        return new OutboxEvent("post-events", "POST_LIKED", "{}");
    }

    @Test
    @DisplayName("PENDING 이벤트 발행 성공 → PUBLISHED 상태로 변경 및 저장")
    @SuppressWarnings("unchecked")
    void publishPendingEvents_성공_PUBLISHED() {
        // given
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING, 100))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        // when
        scheduler.publishPendingEvents();

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("PENDING 이벤트 발행 실패 → retryCount 1 증가, 상태 유지 및 저장")
    void publishPendingEvents_실패_retryCount_증가() {
        // given
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING, 100))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 연결 실패")));

        // when
        scheduler.publishPendingEvents();

        // then
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("3회 연속 실패 → FAILED 상태로 변경, failedAt 기록")
    void publishPendingEvents_3회실패_FAILED() {
        // given
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING, 100))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 연결 실패")));

        // when
        scheduler.publishPendingEvents();
        scheduler.publishPendingEvents();
        scheduler.publishPendingEvents();

        // then
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING 이벤트 없음 → Kafka 발행 없음, 저장 없음")
    void publishPendingEvents_이벤트없음_발행안함() {
        // given
        when(outboxEventRepository.findByStatusOrderByCreatedAt(OutboxStatus.PENDING, 100))
                .thenReturn(List.of());

        // when
        scheduler.publishPendingEvents();

        // then
        verify(kafkaTemplate, never()).send(anyString(), anyString());
        verify(outboxEventRepository, never()).save(any());
    }
}