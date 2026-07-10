package com.sparta.ditto.notification.infrastructure.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatMessageEvent(
        String eventId,
        String eventType,
        String roomId,
        String messageId,
        UUID senderId,
        String senderNickname,
        String senderProfileImageUrl,
        List<UUID> receiverIds,
        String preview
) {}
