package com.sparta.ditto.feed.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventAdapter implements OutboxEventPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public OutboxEvent buildPostCreated(Post post, UUID userId, List<String> tags) {
        record Payload(String postId, String userId, String content, List<String> tags,
                       String neighborhood, String createdAt) {}

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    userId.toString(),
                    post.getContent(),
                    tags,
                    post.getNeighborhood(),
                    post.getCreatedAt() != null ? post.getCreatedAt().toString() : null
            ));
            return new OutboxEvent("post-events", "POST_CREATED", post.getUserId(), payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_CREATED outbox payload 직렬화 실패", e);
        }
    }

    @Override
    public OutboxEvent buildPostDeleted(Post post, UUID deletedBy) {
        record Payload(String postId, String ownerId, String deletedBy,
                       String deleteType, String deletedAt) {}

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    post.getUserId().toString(),
                    deletedBy.toString(),
                    "SOFT",
                    Instant.now().toString()
            ));
            return new OutboxEvent("post-events", "POST_DELETED", post.getUserId(), payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_DELETED outbox payload 직렬화 실패", e);
        }
    }

    @Override
    public OutboxEvent buildPostHardDeleted(Post post, UUID deletedBy) {
        record Payload(String postId, String authorId, String deletedBy) {}

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    post.getUserId().toString(),
                    // hard delete는 시스템(스케줄러)이 수행. 별도 시스템 UUID 상수 미정의이므로
                    // soft delete 요청자(post.getDeletedBy())를 재사용한다.
                    deletedBy != null ? deletedBy.toString() : null
            ));
            return new OutboxEvent("post-events", "POST_HARD_DELETED", post.getUserId(), payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_HARD_DELETED outbox payload 직렬화 실패", e);
        }
    }

    @Override
    public OutboxEvent buildPostRestored(Post post, UUID restoredBy) {
        record Payload(String postId, String authorId, String restoredBy) {}

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    post.getUserId().toString(),
                    restoredBy.toString()
            ));
            return new OutboxEvent("post-events", "POST_RESTORED", post.getUserId(), payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_RESTORED outbox payload 직렬화 실패", e);
        }
    }
}
