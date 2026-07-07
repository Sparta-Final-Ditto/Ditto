package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomKickResult(
        UUID roomId,
        RoomStatus status,
        UUID kickedUserId,
        Instant leftAt,
        String lastVisibleMessageId
) {
    public static ChatRoomKickResult of(
            UUID roomId,
            RoomStatus status,
            UUID kickedUserId,
            Instant leftAt,
            String lastVisibleMessageId
    ) {
        return new ChatRoomKickResult(roomId, status, kickedUserId, leftAt, lastVisibleMessageId);
    }
}
