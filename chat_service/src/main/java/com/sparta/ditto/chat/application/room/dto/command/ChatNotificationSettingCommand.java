package com.sparta.ditto.chat.application.room.dto.command;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;

public record ChatNotificationSettingCommand(
        UUID requesterId,
        UUID roomId,
        boolean enabled
) {

    public static ChatNotificationSettingCommand of(
            UUID requesterId,
            UUID roomId,
            Boolean enabled
    ) {
        if (requesterId == null || roomId == null || enabled == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatNotificationSettingCommand(requesterId, roomId, enabled);
    }
}
