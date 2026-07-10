package com.sparta.ditto.user.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record BlockRelationsResponse(
        List<UUID> blockedUserIds,
        List<UUID> blockedByUserIds
) {
    public static BlockRelationsResponse of(
            List<UUID> blockedUserIds, List<UUID> blockedByUserIds
    ) {
        return new BlockRelationsResponse(blockedUserIds, blockedByUserIds);
    }
}
