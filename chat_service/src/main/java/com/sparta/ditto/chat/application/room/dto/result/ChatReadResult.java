package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;

public record ChatReadResult(
        UUID roomId,
        String lastReadMessageId,
        Instant lastReadAt
) {

    public static ChatReadResult of(
            UUID roomId,
            String lastReadMessageId,
            Instant lastReadAt
    ) {
        if (roomId == null
                || lastReadMessageId == null
                || lastReadMessageId.isBlank()
                || lastReadAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatReadResult(roomId, lastReadMessageId, lastReadAt);
    }
}
