package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatUserNotFoundException extends BusinessException {

    public ChatUserNotFoundException() {
        super(ChatErrorCode.CHAT_USER_NOT_FOUND);
    }
}
