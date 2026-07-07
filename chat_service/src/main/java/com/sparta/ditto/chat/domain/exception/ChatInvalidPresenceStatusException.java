package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatInvalidPresenceStatusException extends BusinessException {

    public ChatInvalidPresenceStatusException() {
        super(ChatErrorCode.CHAT_INVALID_PRESENCE_STATUS);
    }
}
