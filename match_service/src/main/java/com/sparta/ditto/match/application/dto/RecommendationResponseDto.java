package com.sparta.ditto.match.application.dto;

import java.util.UUID;

public record RecommendationResponseDto(
        UUID userId,
        Float score
) {}
