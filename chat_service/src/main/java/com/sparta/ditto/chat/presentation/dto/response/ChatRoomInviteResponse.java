package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.result.ChatRoomInviteResult;
import java.util.List;
import java.util.UUID;

public record ChatRoomInviteResponse(
        UUID roomId,
        List<UUID> invitedUserIds
) {

    public static ChatRoomInviteResponse from(ChatRoomInviteResult result) {
        return new ChatRoomInviteResponse(
                result.roomId(),
                result.invitedUserIds()
        );
    }
}
