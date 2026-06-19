package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatMessageNotFoundException extends BusinessException {

    public ChatMessageNotFoundException() {
        super(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
    }
}
