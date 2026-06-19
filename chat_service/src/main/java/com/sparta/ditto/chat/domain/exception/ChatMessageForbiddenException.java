package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatMessageForbiddenException extends BusinessException {

    public ChatMessageForbiddenException() {
        super(ChatErrorCode.CHAT_MESSAGE_FORBIDDEN);
    }
}
