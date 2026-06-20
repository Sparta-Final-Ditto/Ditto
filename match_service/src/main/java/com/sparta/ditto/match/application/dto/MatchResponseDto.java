package com.sparta.ditto.match.application.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record MatchResponseDto(
        UUID matchId,
        UUID matchedUserId,
        Float similarityScore,
        Float finalScore,
        Instant matchedAt,  // LocalDateTime → Instant
        String status
) {}