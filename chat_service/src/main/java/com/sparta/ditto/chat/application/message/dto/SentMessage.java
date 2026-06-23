package com.sparta.ditto.chat.application.message.dto;

import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.UUID;

public record SentMessage(
        String messageId,
        UUID roomId,
        UUID senderId,
        UUID actorId,
        UUID clientMessageId,
        MessageType messageType,
        String content,
        Instant createdAt,
        Instant deletedAt
) {
}
