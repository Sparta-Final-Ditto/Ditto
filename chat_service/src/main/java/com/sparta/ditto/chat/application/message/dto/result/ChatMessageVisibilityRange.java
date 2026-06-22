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
        boolean left,
        boolean empty
) {

    public static ChatMessageVisibilityRange currentParticipant(
            UUID roomId,
            UUID userId,
            Instant joinedAt
    ) {
        validateBase(roomId, userId, joinedAt);
        return new ChatMessageVisibilityRange(roomId, userId, joinedAt, null, false, false);
    }

    public static ChatMessageVisibilityRange leftParticipant(
            UUID roomId,
            UUID userId,
            Instant joinedAt,
            String lastVisibleMessageId
    ) {
        validateBase(roomId, userId, joinedAt);
        if (lastVisibleMessageId == null || lastVisibleMessageId.isBlank()) {
            return new ChatMessageVisibilityRange(roomId, userId, joinedAt, null, true, true);
        }
        return new ChatMessageVisibilityRange(
                roomId, userId, joinedAt, lastVisibleMessageId, true, false);
    }

    public boolean hasUpperBound() {
        return left && !empty;
    }

    private static void validateBase(UUID roomId, UUID userId, Instant joinedAt) {
        if (roomId == null || userId == null || joinedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
