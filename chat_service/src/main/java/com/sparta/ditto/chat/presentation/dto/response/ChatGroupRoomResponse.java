package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.ChatGroupRoomResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.UUID;

public record ChatGroupRoomResponse(
        UUID roomId,
        RoomType roomType,
        String roomName,
        RoomStatus status
) {

    public static ChatGroupRoomResponse from(ChatGroupRoomResult result) {
        return new ChatGroupRoomResponse(
                result.roomId(),
                result.roomType(),
                result.roomName(),
                result.status()
        );
    }
}
