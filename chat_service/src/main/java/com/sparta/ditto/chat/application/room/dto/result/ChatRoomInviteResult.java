package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;

public record ChatRoomInviteResult(
        UUID roomId,
        List<UUID> invitedUserIds
) {

    public static ChatRoomInviteResult of(UUID roomId, List<UUID> invitedUserIds) {
        if (roomId == null || invitedUserIds == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatRoomInviteResult(roomId, List.copyOf(invitedUserIds));
    }
}
