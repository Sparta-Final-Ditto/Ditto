package com.sparta.ditto.chat.application.room.dto.command;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;

public record ChatPresenceCommand(
        UUID requesterId,
        UUID roomId,
        ChatPresenceStatus status
) {

    public static ChatPresenceCommand of(
            UUID requesterId,
            UUID roomId,
            ChatPresenceStatus status
    ) {
        if (requesterId == null || roomId == null || status == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatPresenceCommand(requesterId, roomId, status);
    }
}
