package com.sparta.ditto.notification.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class RoomIdRequiredException extends BusinessException {

    public RoomIdRequiredException() {
        super(NotificationErrorCode.ROOM_ID_REQUIRED);
    }
}
