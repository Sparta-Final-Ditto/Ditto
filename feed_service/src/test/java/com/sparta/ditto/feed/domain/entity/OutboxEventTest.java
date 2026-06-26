package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    private static final UUID AGGREGATE_ID = UUID.randomUUID();

    @Test
    @DisplayName("OutboxEvent 생성 - 초기 상태 PENDING, retryCount 0, aggregateId 저장")
    void createOutboxEvent() {
        OutboxEvent event = new OutboxEvent("topic", "eventType", AGGREGATE_ID, "{\"key\":\"value\"}");

        assertThat(event.getTopic()).isEqualTo("topic");
        assertThat(event.getEventType()).isEqualTo("eventType");
        assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
        assertThat(event.getPayload()).isEqualTo("{\"key\":\"value\"}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("markPublished - 상태가 PUBLISHED로 변경")
    void markPublished() {
        OutboxEvent event = new OutboxEvent("topic", "eventType", AGGREGATE_ID, "{}");

        event.markPublished();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("incrementRetryCount - MAX_RETRY_COUNT 미만이면 retryCount만 증가")
    void incrementRetryCount_belowMax() {
        OutboxEvent event = new OutboxEvent("topic", "eventType", AGGREGATE_ID, "{}");

        event.incrementRetryCount();

        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("incrementRetryCount - MAX_RETRY_COUNT(3) 도달 시 상태 FAILED로 변경")
    void incrementRetryCount_reachesMax_statusBecomeFailed() {
        OutboxEvent event = new OutboxEvent("topic", "eventType", AGGREGATE_ID, "{}");

        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();

        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getFailedAt()).isNotNull();
    }
}