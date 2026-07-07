package com.sparta.ditto.match.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "추천 유저")
public record RecommendationResponseDto(
        @Schema(description = "추천 유저 ID", example = "770e8400-e29b-41d4-a716-446655440000")
        UUID userId,

        @Schema(description = "추천 점수", example = "0.85", nullable = true)
        Float score
) {}