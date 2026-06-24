package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatUserValidationFailedException extends BusinessException {

    public ChatUserValidationFailedException() {
        super(ChatErrorCode.CHAT_USER_VALIDATION_FAILED);
    }
}
