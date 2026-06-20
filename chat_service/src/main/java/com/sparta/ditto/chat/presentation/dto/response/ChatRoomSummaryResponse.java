package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.ChatRoomSummaryResult;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomSummaryResponse(
        UUID roomId,
        RoomType roomType,
        String roomName,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        boolean notificationEnabled
) {

    public static ChatRoomSummaryResponse from(ChatRoomSummaryResult result) {
        return new ChatRoomSummaryResponse(
                result.roomId(),
                result.roomType(),
                result.roomName(),
                result.lastMessage(),
                result.lastMessageAt(),
                result.unreadCount(),
                result.notificationEnabled()
        );
    }
}
