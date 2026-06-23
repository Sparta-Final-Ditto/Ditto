package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatDuplicateProcessingException extends BusinessException {

    public ChatDuplicateProcessingException() {
        super(ChatErrorCode.CHAT_DUPLICATE_PROCESSING);
    }
}