package com.sparta.ditto.chat.application.room.dto.result;

import java.util.UUID;

public record ChatRoomOwnerTransferResult(
        UUID roomId,
        UUID newOwnerId,
        UUID previousOwnerId
) {
    public static ChatRoomOwnerTransferResult of(
            UUID roomId,
            UUID newOwnerId,
            UUID previousOwnerId
    ) {
        return new ChatRoomOwnerTransferResult(roomId, newOwnerId, previousOwnerId);
    }
}
