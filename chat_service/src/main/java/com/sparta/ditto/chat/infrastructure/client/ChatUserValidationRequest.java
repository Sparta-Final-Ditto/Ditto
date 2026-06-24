package com.sparta.ditto.chat.infrastructure.client;

import java.util.List;
import java.util.UUID;

public record ChatUserValidationRequest(
        UUID requesterId,
        List<UUID> targetUserIds,
        boolean checkBlock
) {
}
