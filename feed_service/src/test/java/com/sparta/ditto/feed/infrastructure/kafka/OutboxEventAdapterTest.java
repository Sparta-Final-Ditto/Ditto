package com.sparta.ditto.feed.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.domain.type.OutboxStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventAdapterTest {

    private OutboxEventAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID postId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        adapter = new OutboxEventAdapter();
    }

    private Post createPost(UUID authorId) {
        Post post = new Post(authorId, "닉네임", "오늘 러닝 완료", "서울 성동구",
                37.5563, 127.0374, Visibility.PUBLIC, true);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    @Test
    @DisplayName("buildPostCreated - topic, eventType, PENDING 상태, aggregateId=작성자ID 검증")
    void buildPostCreated_topic_eventType_상태_검증() {
        // given
        Post post = createPost(userId);

        // when
        OutboxEvent event = adapter.buildPostCreated(post, userId, List.of("#러닝"));

        // then
        assertThat(event.getTopic()).isEqualTo("post-events");
        assertThat(event.getEventType()).isEqualTo("POST_CREATED");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAggregateId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("buildPostCreated - payload 필드(postId, userId, content, tags, neighborhood, createdAt) 검증")
    void buildPostCreated_payload_필드_검증() throws Exception {
        // given
        Post post = createPost(userId);
        List<String> tags = List.of("#러닝", "#새벽");

        // when
        OutboxEvent event = adapter.buildPostCreated(post, userId, tags);
        JsonNode payload = objectMapper.readTree(event.getPayload());

        // then
        assertThat(payload.get("postId").asText()).isEqualTo(postId.toString());
        assertThat(payload.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(payload.get("content").asText()).isEqualTo("오늘 러닝 완료");
        assertThat(payload.get("neighborhood").asText()).isEqualTo("서울 성동구");
        assertThat(payload.get("tags").get(0).asText()).isEqualTo("#러닝");
        assertThat(payload.get("tags").get(1).asText()).isEqualTo("#새벽");
    }

    @Test
    @DisplayName("buildPostDeleted - topic, eventType, PENDING 상태, aggregateId=작성자ID 검증")
    void buildPostDeleted_topic_eventType_상태_검증() {
        // given
        Post post = createPost(ownerId);

        // when
        OutboxEvent event = adapter.buildPostDeleted(post, userId);

        // then
        assertThat(event.getTopic()).isEqualTo("post-events");
        assertThat(event.getEventType()).isEqualTo("POST_DELETED");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAggregateId()).isEqualTo(ownerId);
    }

    @Test
    @DisplayName("buildPostDeleted - payload 필드(postId, ownerId, deletedBy, deleteType, deletedAt) 검증")
    void buildPostDeleted_payload_필드_검증() throws Exception {
        // given
        Post post = createPost(ownerId);

        // when
        OutboxEvent event = adapter.buildPostDeleted(post, userId);
        JsonNode payload = objectMapper.readTree(event.getPayload());

        // then
        assertThat(payload.get("postId").asText()).isEqualTo(postId.toString());
        assertThat(payload.get("ownerId").asText()).isEqualTo(ownerId.toString());
        assertThat(payload.get("deletedBy").asText()).isEqualTo(userId.toString());
        assertThat(payload.get("deleteType").asText()).isEqualTo("SOFT");
        assertThat(payload.get("deletedAt").asText()).isNotBlank();
    }
}