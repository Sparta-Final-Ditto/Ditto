package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceStatus;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;

public record ChatPresenceResult(
        UUID roomId,
        ChatPresenceStatus status
) {

    public static ChatPresenceResult of(UUID roomId, ChatPresenceStatus status) {
        if (roomId == null || status == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatPresenceResult(roomId, status);
    }
}
