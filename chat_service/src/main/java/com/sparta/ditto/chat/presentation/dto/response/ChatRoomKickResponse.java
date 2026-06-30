package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.result.ChatRoomKickResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomKickResponse(
        UUID roomId,
        RoomStatus status,
        UUID kickedUserId,
        Instant leftAt,
        String lastVisibleMessageId
) {
    public static ChatRoomKickResponse from(ChatRoomKickResult result) {
        return new ChatRoomKickResponse(
                result.roomId(),
                result.status(),
                result.kickedUserId(),
                result.leftAt(),
                result.lastVisibleMessageId()
        );
    }
}
