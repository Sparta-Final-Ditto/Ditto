package com.sparta.ditto.chat.application.room.dto;

import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.util.UUID;

public record ChatDirectRoomResult(
        UUID roomId,
        RoomStatus status,
        boolean created,
        boolean reactivated
) {

    public static ChatDirectRoomResult of(
            UUID roomId,
            RoomStatus status,
            boolean created,
            boolean reactivated
    ) {
        return new ChatDirectRoomResult(roomId, status, created, reactivated);
    }
}
