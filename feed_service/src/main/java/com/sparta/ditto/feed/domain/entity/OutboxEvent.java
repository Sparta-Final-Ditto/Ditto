package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.type.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_events_status_created_at",
                        columnList = "status, created_at")
        }
)
/**
 * Transactional Outbox 패턴 이벤트 엔티티.
 * Post 저장과 동일 트랜잭션에서 함께 INSERT되어 Kafka 발행 유실을 방지한다.
 * 상태는 PENDING → PUBLISHED 또는 FAILED로 전이되며, 최대 3회까지 재시도한다.
 * 별도 배치 프로세스가 PENDING 이벤트를 조회해 Kafka로 발행한다.
 */
public class OutboxEvent {

    private static final int MAX_RETRY_COUNT = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private UUID aggregateId;

    //TODO: Hibernate 전용 어노테이션 사용 제거
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    private Instant failedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public OutboxEvent(String topic, String eventType, UUID aggregateId, String payload) {
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = OutboxStatus.FAILED;
            this.failedAt = Instant.now();
        }
    }

    public void resetToPending() {
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.failedAt = null;
    }
}
