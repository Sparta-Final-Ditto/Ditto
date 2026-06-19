package com.sparta.ditto.chat.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChatDirectRoomCreateRequest(
        @NotNull
        UUID targetUserId
) {
}
