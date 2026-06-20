package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomLeaveResult(
        UUID roomId,
        RoomStatus status,
        Instant leftAt,
        String lastVisibleMessageId
) {

    public static ChatRoomLeaveResult of(
            UUID roomId,
            RoomStatus status,
            Instant leftAt,
            String lastVisibleMessageId
    ) {
        return new ChatRoomLeaveResult(roomId, status, leftAt, lastVisibleMessageId);
    }
}
