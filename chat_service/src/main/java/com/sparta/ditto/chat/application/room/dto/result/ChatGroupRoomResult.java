package com.sparta.ditto.chat.application.room.dto.result;

import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;

public record ChatGroupRoomResult(
        UUID roomId,
        RoomType roomType,
        String roomName,
        RoomStatus status
) {

    public static ChatGroupRoomResult of(
            UUID roomId,
            RoomType roomType,
            String roomName,
            RoomStatus status
    ) {
        if (roomId == null || roomType == null || roomName == null || status == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return new ChatGroupRoomResult(roomId, roomType, roomName, status);
    }
}
