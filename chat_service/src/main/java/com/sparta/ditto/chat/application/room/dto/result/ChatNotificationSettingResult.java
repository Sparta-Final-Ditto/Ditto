package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;

public record ChatNotificationSettingResult(
        UUID roomId,
        boolean notificationEnabled
) {

    public static ChatNotificationSettingResult of(UUID roomId, boolean notificationEnabled) {
        if (roomId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatNotificationSettingResult(roomId, notificationEnabled);
    }
}
