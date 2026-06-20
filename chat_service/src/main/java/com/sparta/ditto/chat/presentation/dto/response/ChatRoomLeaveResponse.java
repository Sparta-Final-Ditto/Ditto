package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.ChatRoomLeaveResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomLeaveResponse(
        UUID roomId,
        RoomStatus status,
        Instant leftAt,
        String lastVisibleMessageId
) {

    public static ChatRoomLeaveResponse from(ChatRoomLeaveResult result) {
        return new ChatRoomLeaveResponse(
                result.roomId(),
                result.status(),
                result.leftAt(),
                result.lastVisibleMessageId()
        );
    }
}
