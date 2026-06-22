package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.result.ChatDirectRoomResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.util.UUID;

public record ChatDirectRoomResponse(
        UUID roomId,
        RoomStatus status,
        boolean reactivated
) {

    public static ChatDirectRoomResponse from(ChatDirectRoomResult result) {
        return new ChatDirectRoomResponse(
                result.roomId(),
                result.status(),
                result.reactivated()
        );
    }
}
