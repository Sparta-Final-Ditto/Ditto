package com.sparta.ditto.chat.presentation.dto.stomp;

import jakarta.validation.constraints.NotBlank;

public record ChatReadRequest(
        @NotBlank
        String lastReadMessageId
) {
}
