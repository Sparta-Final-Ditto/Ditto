package com.sparta.ditto.notification.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReadByRoomRequest(
        @NotBlank String roomId
) {
}