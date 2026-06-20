package com.sparta.ditto.chat.application.room.dto;

import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import java.time.Instant;
import java.util.UUID;

public record ChatParticipantResult(
        UUID userId,
        ParticipantRole role,
        Instant joinedAt,
        Instant leftAt
) {

    public static ChatParticipantResult of(
            UUID userId,
            ParticipantRole role,
            Instant joinedAt,
            Instant leftAt
    ) {
        return new ChatParticipantResult(userId, role, joinedAt, leftAt);
    }
}
