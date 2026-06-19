package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
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
    public static ChatMessageResponse from(ChatMessageDocument document) {
        return new ChatMessageResponse(
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
