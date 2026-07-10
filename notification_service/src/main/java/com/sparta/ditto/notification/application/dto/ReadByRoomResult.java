package com.sparta.ditto.notification.application.dto;

public record ReadByRoomResult(
        String roomId,
        int updatedCount
) {
    public static ReadByRoomResult of(String roomId, int updatedCount) {
        return new ReadByRoomResult(roomId, updatedCount);
    }
}
