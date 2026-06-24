package com.sparta.ditto.feed.application.port.out.dto;

import java.util.List;
import java.util.UUID;

public record RecommendationResult(
        List<UUID> recommendedUserIds
) {}
