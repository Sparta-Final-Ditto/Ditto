package com.sparta.ditto.chat.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ChatGroupRoomCreateRequest(
        @NotEmpty
        List<@NotNull UUID> participantUserIds,

        @NotBlank
        String roomName
) {
}
