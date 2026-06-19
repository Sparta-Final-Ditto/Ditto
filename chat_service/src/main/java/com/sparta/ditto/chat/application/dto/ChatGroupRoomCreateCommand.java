package com.sparta.ditto.chat.application.dto;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ChatGroupRoomCreateCommand(
        UUID requesterId,
        List<UUID> participantUserIds,
        String roomName
) {

    public static ChatGroupRoomCreateCommand of(
            UUID requesterId,
            List<UUID> participantUserIds,
            String roomName
    ) {
        if (roomName == null || roomName.isBlank()) {
            throw new IllegalArgumentException("roomName must not be blank");
        }
        return new ChatGroupRoomCreateCommand(
                Objects.requireNonNull(requesterId, "requesterId must not be null"),
                List.copyOf(Objects.requireNonNull(
                        participantUserIds,
                        "participantUserIds must not be null"
                )),
                roomName
        );
    }
}
