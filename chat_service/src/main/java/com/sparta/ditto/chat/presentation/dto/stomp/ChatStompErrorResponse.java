package com.sparta.ditto.chat.presentation.dto.stomp;

import java.time.Instant;
import java.util.UUID;

public record ChatStompErrorResponse(
        String code,
        String errorType,
        String message,
        Instant timestamp,
        UUID roomId,
        UUID clientMessageId
) {
    public static ChatStompErrorResponse of(
            String code,
            String errorType,
            String message,
            UUID roomId,
            UUID clientMessageId
    ) {
        return new ChatStompErrorResponse(
                code, errorType, message, Instant.now(), roomId, clientMessageId);
    }
}
