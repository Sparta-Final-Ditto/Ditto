package com.sparta.ditto.match.infrastructure.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostCreatedEvent(
        String eventId,
        String eventType,
        String occurredAt,
        Payload payload
) {
    public record Payload(
            UUID postId,
            UUID userId,
            String content,
            List<String> tags,
            String neighborhood,
            Double latitude,
            Double longitude,
            Instant createdAt
    ) {}

    public UUID getUserId() {
        return payload().userId();
    }

    public List<String> getTags() {
        return payload().tags();
    }

    public Instant getCreatedAt() {
        return payload().createdAt();
    }
}
