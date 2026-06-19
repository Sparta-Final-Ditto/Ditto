package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatRoomInactiveException extends BusinessException {

    public ChatRoomInactiveException() {
        super(ChatErrorCode.CHAT_ROOM_INACTIVE);
    }
}
