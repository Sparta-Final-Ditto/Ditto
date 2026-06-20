package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.ChatRoomDetailResult;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.List;
import java.util.UUID;

public record ChatRoomResponse(
        UUID roomId,
        RoomType roomType,
        String roomName,
        RoomStatus status,
        List<ChatParticipantResponse> participants,
        boolean notificationEnabled
) {

    public static ChatRoomResponse from(ChatRoomDetailResult result) {
        return new ChatRoomResponse(
                result.roomId(),
                result.roomType(),
                result.roomName(),
                result.status(),
                result.participants()
                        .stream()
                        .map(ChatParticipantResponse::from)
                        .toList(),
                result.notificationEnabled()
        );
    }
}
