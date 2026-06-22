package com.sparta.ditto.chat.presentation.dto.stomp;

import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceStatus;
import jakarta.validation.constraints.NotNull;

public record ChatPresenceRequest(
        @NotNull
        ChatPresenceStatus status
) {
}
