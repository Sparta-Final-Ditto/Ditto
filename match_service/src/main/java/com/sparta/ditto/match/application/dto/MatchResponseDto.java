package com.sparta.ditto.match.application.dto;

import com.sparta.ditto.match.domain.entity.MatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "매칭 결과")
public record MatchResponseDto(
        @Schema(description = "매칭 ID", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID matchId,

        @Schema(description = "매칭된 유저 ID", example = "660e8400-e29b-41d4-a716-446655440000")
        UUID matchedUserId,

        @Schema(description = "벡터 유사도 점수", example = "0.87")
        float similarityScore,

        @Schema(description = "최종 점수 (벡터 50% + 태그 50%)", example = "0.82")
        float finalScore,

        @Schema(description = "매칭 시각")
        Instant matchedAt,

        @Schema(description = "매칭 상태", example = "PENDING")
        MatchStatus status,

        @Schema(description = "AI 매칭 설명 (RAG)", example = "여행과 사진을 함께 즐기는 감성이 딱 맞는 두 분이에요!")
        String explanation
) {}