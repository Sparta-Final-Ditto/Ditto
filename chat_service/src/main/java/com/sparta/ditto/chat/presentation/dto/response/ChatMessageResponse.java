package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageResult;
import com.sparta.ditto.chat.domain.message.MessageType;
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
    public static ChatMessageResponse from(SentMessage message) {
        return new ChatMessageResponse(
                message.messageId(),
                message.roomId(),
                message.senderId(),
                message.actorId(),
                message.clientMessageId(),
                message.messageType(),
                message.content(),
                message.createdAt(),
                message.deletedAt()
        );
    }

    public static ChatMessageResponse from(ChatMessageResult message) {
        return new ChatMessageResponse(
                message.messageId(),
                message.roomId(),
                message.senderId(),
                message.actorId(),
                message.clientMessageId(),
                message.messageType(),
                message.content(),
                message.createdAt(),
                message.deletedAt()
        );
    }
}
