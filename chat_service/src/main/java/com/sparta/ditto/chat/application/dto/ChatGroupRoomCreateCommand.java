package com.sparta.ditto.chat.application.dto;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;

public record ChatGroupRoomCreateCommand(
        UUID requesterId,
        List<UUID> participantUserIds,
        String roomName
) {

    public static ChatGroupRoomCreateCommand of(
            UUID requesterId,
            List<UUID> participantUserIds,
            String roomName
    ) {
        if (requesterId == null || participantUserIds == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (roomName == null || roomName.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatGroupRoomCreateCommand(
                requesterId,
                List.copyOf(participantUserIds),
                roomName
        );
    }
}
