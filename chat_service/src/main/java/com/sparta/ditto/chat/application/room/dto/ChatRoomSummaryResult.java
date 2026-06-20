package com.sparta.ditto.chat.application.room.dto;

import com.sparta.ditto.chat.domain.room.RoomType;
import java.time.Instant;
import java.util.UUID;

public record ChatRoomSummaryResult(
        UUID roomId,
        RoomType roomType,
        String roomName,
        String lastMessage,
        Instant lastMessageAt,
        long unreadCount,
        boolean notificationEnabled
) {

    public static ChatRoomSummaryResult of(
            UUID roomId,
            RoomType roomType,
            String roomName,
            String lastMessage,
            Instant lastMessageAt,
            long unreadCount,
            boolean notificationEnabled
    ) {
        return new ChatRoomSummaryResult(
                roomId,
                roomType,
                roomName,
                lastMessage,
                lastMessageAt,
                unreadCount,
                notificationEnabled
        );
    }
}
