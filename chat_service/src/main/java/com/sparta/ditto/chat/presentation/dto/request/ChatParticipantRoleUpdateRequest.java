package com.sparta.ditto.chat.presentation.dto.request;

import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record ChatParticipantRoleUpdateRequest(
        @NotNull
        ParticipantRole role
) {
}
