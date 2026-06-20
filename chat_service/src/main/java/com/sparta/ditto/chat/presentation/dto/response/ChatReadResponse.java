package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import java.time.Instant;
import java.util.UUID;

public record ChatReadResponse(
        UUID roomId,
        String lastReadMessageId,
        Instant lastReadAt
) {

    public static ChatReadResponse from(ChatReadResult result) {
        return new ChatReadResponse(
                result.roomId(),
                result.lastReadMessageId(),
                result.lastReadAt()
        );
    }
}
