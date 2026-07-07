package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatBlockedUserException extends BusinessException {

    public ChatBlockedUserException() {
        super(ChatErrorCode.CHAT_BLOCKED_USER);
    }
}
