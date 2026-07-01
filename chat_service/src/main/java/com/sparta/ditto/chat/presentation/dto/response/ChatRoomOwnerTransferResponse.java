package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.result.ChatRoomOwnerTransferResult;
import java.util.UUID;

public record ChatRoomOwnerTransferResponse(
        UUID roomId,
        UUID newOwnerId,
        UUID previousOwnerId
) {
    public static ChatRoomOwnerTransferResponse from(ChatRoomOwnerTransferResult result) {
        return new ChatRoomOwnerTransferResponse(
                result.roomId(),
                result.newOwnerId(),
                result.previousOwnerId()
        );
    }
}
