package com.sparta.ditto.chat.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChatNotificationSettingRequest(
        @NotNull
        Boolean enabled
) {
}
