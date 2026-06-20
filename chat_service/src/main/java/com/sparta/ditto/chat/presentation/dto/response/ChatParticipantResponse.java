package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.room.dto.ChatParticipantResult;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import java.time.Instant;
import java.util.UUID;

public record ChatParticipantResponse(
        UUID userId,
        ParticipantRole role,
        Instant joinedAt,
        Instant leftAt
) {

    public static ChatParticipantResponse from(ChatParticipantResult result) {
        return new ChatParticipantResponse(
                result.userId(),
                result.role(),
                result.joinedAt(),
                result.leftAt()
        );
    }
}
