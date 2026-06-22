package com.sparta.ditto.chat.application.message.dto;

import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
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
    public static SentMessage from(ChatMessageDocument document) {
        return new SentMessage(
                document.getMessageId(),
                document.getRoomId(),
                document.getSenderId(),
                document.getActorId(),
                document.getClientMessageId(),
                document.getMessageType(),
                document.getContent(),
                document.getCreatedAt(),
                document.getDeletedAt()
        );
    }
}
