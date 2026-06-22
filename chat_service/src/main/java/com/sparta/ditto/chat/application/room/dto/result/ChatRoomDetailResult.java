package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.List;
import java.util.UUID;

public record ChatRoomDetailResult(
        UUID roomId,
        RoomType roomType,
        String roomName,
        RoomStatus status,
        List<ChatParticipantResult> participants,
        boolean notificationEnabled
) {

    public static ChatRoomDetailResult of(
            UUID roomId,
            RoomType roomType,
            String roomName,
            RoomStatus status,
            List<ChatParticipantResult> participants,
            boolean notificationEnabled
    ) {
        return new ChatRoomDetailResult(
                roomId,
                roomType,
                roomName,
                status,
                List.copyOf(participants),
                notificationEnabled
        );
    }
}
