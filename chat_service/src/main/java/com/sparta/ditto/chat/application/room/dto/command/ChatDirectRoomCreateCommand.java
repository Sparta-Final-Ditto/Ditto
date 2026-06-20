package com.sparta.ditto.chat.application.room.dto.command;

import java.util.UUID;

public record ChatDirectRoomCreateCommand(
        UUID requesterId,
        UUID targetUserId
) {

    public static ChatDirectRoomCreateCommand of(UUID requesterId, UUID targetUserId) {
        return new ChatDirectRoomCreateCommand(requesterId, targetUserId);
    }
}
