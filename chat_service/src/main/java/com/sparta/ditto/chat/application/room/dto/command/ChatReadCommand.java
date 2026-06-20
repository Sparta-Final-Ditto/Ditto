package com.sparta.ditto.chat.application.room.dto.command;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;

public record ChatReadCommand(
        UUID requesterId,
        UUID roomId,
        String lastReadMessageId
) {

    public static ChatReadCommand of(
            UUID requesterId,
            UUID roomId,
            String lastReadMessageId
    ) {
        if (requesterId == null
                || roomId == null
                || lastReadMessageId == null
                || lastReadMessageId.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatReadCommand(requesterId, roomId, lastReadMessageId);
    }
}
