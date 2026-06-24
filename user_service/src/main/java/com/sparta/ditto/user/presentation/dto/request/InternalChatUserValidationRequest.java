package com.sparta.ditto.user.presentation.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record InternalChatUserValidationRequest(
        @NotNull UUID requesterId,
        @NotEmpty List<@NotNull UUID> targetUserIds,
        boolean checkBlock
) {
}
