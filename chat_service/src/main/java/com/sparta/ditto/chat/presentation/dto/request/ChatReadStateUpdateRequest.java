package com.sparta.ditto.chat.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChatReadStateUpdateRequest(
        @NotBlank
        String lastReadMessageId
) {
}
