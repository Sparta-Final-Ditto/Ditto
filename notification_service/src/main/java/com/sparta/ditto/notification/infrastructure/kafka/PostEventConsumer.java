package com.sparta.ditto.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.application.NotificationEventHandler;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.infrastructure.kafka.dto.PostEventEnvelope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventHandler notificationEventHandler;

    @KafkaListener(topics = "post-events")
    public void consume(String message) throws Exception {
        PostEventEnvelope envelope = objectMapper.readValue(message, PostEventEnvelope.class);
        PostNotificationCommand cmd = toCommand(envelope);
        notificationEventHandler.handlePostEvent(cmd);
    }

    private static PostNotificationCommand toCommand(PostEventEnvelope envelope) {
        String eventType = envelope.eventType();
        JsonNode payload = envelope.payload();

        String targetId;
        if ("POST_LIKED".equals(eventType)) {
            targetId = textOrNull(payload, "likeId");
        } else if ("POST_COMMENTED".equals(eventType)) {
            targetId = textOrNull(payload, "commentId");
        } else {
            targetId = null;
        }

        return PostNotificationCommand.of(
                eventType,
                targetId,
                textOrNull(payload, "postId"),
                uuidOrNull(payload, "userId"),
                textOrNull(payload, "actorNickname"),
                uuidOrNull(payload, "ownerId")
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return (child.isMissingNode() || child.isNull()) ? null : child.asText();
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        String text = textOrNull(node, field);
        return text == null ? null : UUID.fromString(text);
    }
}
