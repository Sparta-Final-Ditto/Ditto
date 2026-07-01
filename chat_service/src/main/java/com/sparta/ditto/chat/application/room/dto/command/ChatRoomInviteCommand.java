package com.sparta.ditto.chat.application.room.dto.command;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;

public record ChatRoomInviteCommand(
        UUID requesterId,
        UUID roomId,
        List<UUID> targetUserIds
) {

    public static ChatRoomInviteCommand of(
            UUID requesterId,
            UUID roomId,
            List<UUID> targetUserIds
    ) {
        if (requesterId == null || roomId == null || targetUserIds == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatRoomInviteCommand(
                requesterId,
                roomId,
                List.copyOf(targetUserIds)
        );
    }
}
