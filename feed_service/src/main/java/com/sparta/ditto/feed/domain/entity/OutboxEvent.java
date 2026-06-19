package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.type.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime publishedAt;

    private LocalDateTime failedAt;

    public OutboxEvent(String topic, String eventType, String payload) {
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = OutboxStatus.FAILED;
            this.failedAt = LocalDateTime.now();
        }
    }
}
