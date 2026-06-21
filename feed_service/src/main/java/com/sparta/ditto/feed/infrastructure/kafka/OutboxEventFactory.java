package com.sparta.ditto.feed.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Outbox 이벤트 페이로드 직렬화 및 생성 유틸리티 */
public class OutboxEventFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OutboxEventFactory() {}

    public static OutboxEvent createPostLiked(Post post, UUID likerId) {
        record Payload(String postId, String userId, String ownerId, String likedAt) {}

        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    likerId.toString(),
                    post.getUserId().toString(),
                    Instant.now().toString()
            ));
            return new OutboxEvent("post-events", "POST_LIKED", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_LIKED outbox payload 직렬화 실패", e);
        }
    }

    public static OutboxEvent createPostCreated(Post post, UUID userId, List<String> tags) {
        record Payload(String postId, String userId, String content, List<String> tags,
                       String neighborhood, Double latitude, Double longitude, String createdAt) {}
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(), userId.toString(),
                    post.getContent(), tags, post.getNeighborhood(),
                    post.getLatitude(), post.getLongitude(),
                    post.getCreatedAt() != null ? post.getCreatedAt().toString() : null
            ));
            return new OutboxEvent("post-events", "POST_CREATED", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_CREATED outbox payload 직렬화 실패", e);
        }
    }
}