package com.sparta.ditto.feed.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.event.PostCommentedEvent;
import com.sparta.ditto.feed.application.event.PostLikedEvent;
import com.sparta.ditto.feed.application.port.NotificationEventPublisher;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationEventKafkaPublisher implements NotificationEventPublisher {

    private static final String TOPIC = "post-events";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    public NotificationEventKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishPostLiked(PostLikedEvent event) {
        record Payload(String postId, String userId, String ownerId, String likedAt) {}

        record Envelope(String eventId, String eventType, String occurredAt, Payload payload) {}

        try {
            String message = OBJECT_MAPPER.writeValueAsString(new Envelope(
                    UUID.randomUUID().toString(),
                    "POST_LIKED",
                    Instant.now().toString(),
                    new Payload(
                            event.postId().toString(),
                            event.likerId().toString(),
                            event.ownerId().toString(),
                            event.likedAt().toString()
                    )
            ));
            kafkaTemplate.send(TOPIC, event.ownerId().toString(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("POST_LIKED 발행 실패. postId={}, likerId={}",
                                    event.postId(), event.likerId(), ex);
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("POST_LIKED payload 직렬화 실패. postId={}", event.postId(), e);
        }
    }

    @Override
    public void publishPostCommented(PostCommentedEvent event) {
        record Payload(
                String postId, String commentId, String userId, String ownerId, String commentedAt
        ) {}

        record Envelope(String eventId, String eventType, String occurredAt, Payload payload) {}

        try {
            String message = OBJECT_MAPPER.writeValueAsString(new Envelope(
                    UUID.randomUUID().toString(),
                    "POST_COMMENTED",
                    Instant.now().toString(),
                    new Payload(
                            event.postId().toString(),
                            event.commentId().toString(),
                            event.commenterId().toString(),
                            event.ownerId().toString(),
                            event.commentedAt().toString()
                    )
            ));
            kafkaTemplate.send(TOPIC, event.ownerId().toString(), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("POST_COMMENTED 발행 실패. postId={}, commentId={}",
                                    event.postId(), event.commentId(), ex);
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("POST_COMMENTED payload 직렬화 실패. postId={}", event.postId(), e);
        }
    }
}
