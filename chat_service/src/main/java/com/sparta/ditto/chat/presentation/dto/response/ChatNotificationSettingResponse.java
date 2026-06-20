package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.result.ChatNotificationSettingResult;
import java.util.UUID;

public record ChatNotificationSettingResponse(
        UUID roomId,
        boolean notificationEnabled
) {

    public static ChatNotificationSettingResponse from(ChatNotificationSettingResult result) {
        return new ChatNotificationSettingResponse(
                result.roomId(),
                result.notificationEnabled()
        );
    }
}
