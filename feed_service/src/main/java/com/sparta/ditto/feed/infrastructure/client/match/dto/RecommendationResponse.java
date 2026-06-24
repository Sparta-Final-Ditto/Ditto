package com.sparta.ditto.feed.infrastructure.client.match.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationResponse(
        int status,
        String message,
        List<RecommendedUser> data
) {
    public record RecommendedUser(UUID userId) {}
}
