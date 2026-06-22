package com.sparta.ditto.chat.application.message.dto.result;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;

public record ChatMessageVisibilityRange(
        UUID roomId,
        UUID userId,
        Instant joinedAt,
        String lastVisibleMessageId,
        boolean left
) {

    public static ChatMessageVisibilityRange currentParticipant(
            UUID roomId,
            UUID userId,
            Instant joinedAt
    ) {
        validateBase(roomId, userId, joinedAt);
        return new ChatMessageVisibilityRange(roomId, userId, joinedAt, null, false);
    }

    public static ChatMessageVisibilityRange leftParticipant(
            UUID roomId,
            UUID userId,
            Instant joinedAt,
            String lastVisibleMessageId
    ) {
        validateBase(roomId, userId, joinedAt);
        if (lastVisibleMessageId == null || lastVisibleMessageId.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatMessageVisibilityRange(roomId, userId, joinedAt, lastVisibleMessageId, true);
    }

    public boolean hasUpperBound() {
        return left;
    }

    private static void validateBase(UUID roomId, UUID userId, Instant joinedAt) {
        if (roomId == null || userId == null || joinedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
