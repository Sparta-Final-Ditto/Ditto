package com.sparta.ditto.notification.presentation.dto.response;

import com.sparta.ditto.notification.application.dto.ReadByRoomResult;

public record ReadByRoomResponse(
        String roomId,
        int updatedCount
) {
    public static ReadByRoomResponse from(ReadByRoomResult result) {
        return new ReadByRoomResponse(result.roomId(), result.updatedCount());
    }
}
