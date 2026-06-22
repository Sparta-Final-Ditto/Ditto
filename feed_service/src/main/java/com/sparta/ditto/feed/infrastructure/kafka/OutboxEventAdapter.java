package com.sparta.ditto.feed.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.port.OutboxEventPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventAdapter implements OutboxEventPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public OutboxEvent buildPostLiked(Post post, UUID likerId) {
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

    @Override
    public OutboxEvent buildPostCreated(Post post, UUID userId, List<String> tags) {
        record Payload(String postId, String userId, String content, List<String> tags,
                       String neighborhood, Double latitude, Double longitude, String createdAt) {}
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    userId.toString(),
                    post.getContent(),
                    tags,
                    post.getNeighborhood(),
                    post.getLatitude(),
                    post.getLongitude(),
                    post.getCreatedAt() != null ? post.getCreatedAt().toString() : null
            ));
            return new OutboxEvent("post-events", "POST_CREATED", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_CREATED outbox payload 직렬화 실패", e);
        }
    }

    @Override
    public OutboxEvent buildPostCommented(Post post, Comment comment, UUID commenterId) {
        record Payload(String postId, String commentId, String userId, String ownerId, String commentedAt) {}
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new Payload(
                    post.getId().toString(),
                    comment.getId().toString(),
                    commenterId.toString(),
                    post.getUserId().toString(),
                    Instant.now().toString()
            ));
            return new OutboxEvent("post-events", "POST_COMMENTED", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("POST_COMMENTED outbox payload 직렬화 실패", e);
        }
    }
}