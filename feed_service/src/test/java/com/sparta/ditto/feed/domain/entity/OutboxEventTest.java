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

    @Test
    @DisplayName("incrementRetryCount - replayCount가 MAX(3) 이상이면 3회 실패 시 DEAD로 전환")
    void incrementRetryCount_replayCountMax_3회실패_DEAD() {
        // given - 3회 replay 사이클 소진: FAILED→PENDING을 3번 반복
        OutboxEvent event = new OutboxEvent("topic", "eventType", AGGREGATE_ID, "{}");
        for (int i = 0; i < 3; i++) {
            event.incrementRetryCount();
            event.incrementRetryCount();
            event.incrementRetryCount();
            event.resetToPending();
        }
        // replayCount=3, status=PENDING, retryCount=0

        // when - 4번째 실패 사이클
        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();

        // then
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getReplayCount()).isEqualTo(3);
        assertThat(event.getFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("resetToPending - DEAD 상태에서는 상태·replayCount 변화 없음")
    void resetToPending_DEAD_상태에서_변화없음() {
        // given - DEAD 상태 생성
        OutboxEvent event = new OutboxEvent("topic", "eventType", AGGREGATE_ID, "{}");
        for (int i = 0; i < 3; i++) {
            event.incrementRetryCount();
            event.incrementRetryCount();
            event.incrementRetryCount();
            event.resetToPending();
        }
        event.incrementRetryCount();
        event.incrementRetryCount();
        event.incrementRetryCount();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);

        // when
        event.resetToPending();

        // then - no-op
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
        assertThat(event.getReplayCount()).isEqualTo(3);
    }
}