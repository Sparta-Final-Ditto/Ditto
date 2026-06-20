package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.Objects;
import java.util.UUID;

public record ChatGroupRoomResult(
        UUID roomId,
        RoomType roomType,
        String roomName,
        RoomStatus status
) {

    public static ChatGroupRoomResult of(
            UUID roomId,
            RoomType roomType,
            String roomName,
            RoomStatus status
    ) {
        return new ChatGroupRoomResult(
                Objects.requireNonNull(roomId, "roomId must not be null"),
                Objects.requireNonNull(roomType, "roomType must not be null"),
                Objects.requireNonNull(roomName, "roomName must not be null"),
                Objects.requireNonNull(status, "status must not be null")
        );
    }
}
