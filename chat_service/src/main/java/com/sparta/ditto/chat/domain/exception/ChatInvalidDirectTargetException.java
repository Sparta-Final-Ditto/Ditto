package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatInvalidDirectTargetException extends BusinessException {

    public ChatInvalidDirectTargetException() {
        super(ChatErrorCode.CHAT_INVALID_DIRECT_TARGET);
    }
}
