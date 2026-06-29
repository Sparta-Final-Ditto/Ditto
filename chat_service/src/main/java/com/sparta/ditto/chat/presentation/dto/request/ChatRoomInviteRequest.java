package com.sparta.ditto.chat.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ChatRoomInviteRequest(
        @NotEmpty
        List<@NotNull UUID> userIds
) {
}
