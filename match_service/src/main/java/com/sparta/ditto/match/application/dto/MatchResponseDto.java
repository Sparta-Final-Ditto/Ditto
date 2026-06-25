package com.sparta.ditto.match.application.dto;

import com.sparta.ditto.match.domain.entity.MatchStatus;

import java.time.Instant;
import java.util.UUID;

public record MatchResponseDto(
        UUID matchId,
        UUID matchedUserId,
        Float similarityScore,
        Float finalScore,
        Instant matchedAt,
        MatchStatus status,
        String explanation  // 추가
) {}