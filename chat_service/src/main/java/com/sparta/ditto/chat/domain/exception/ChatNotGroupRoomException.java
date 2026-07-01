package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatNotGroupRoomException extends BusinessException {

    public ChatNotGroupRoomException() {
        super(ChatErrorCode.CHAT_NOT_GROUP_ROOM);
    }
}
