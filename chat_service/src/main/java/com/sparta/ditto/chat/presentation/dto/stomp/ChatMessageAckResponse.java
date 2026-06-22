package com.sparta.ditto.chat.presentation.dto.stomp;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageAckResponse(
        UUID roomId,
        UUID clientMessageId,
        String messageId,
        Instant sentAt
) {
    public static ChatMessageAckResponse of(
            UUID roomId, UUID clientMessageId, String messageId, Instant sentAt) {
        return new ChatMessageAckResponse(roomId, clientMessageId, messageId, sentAt);
    }
}