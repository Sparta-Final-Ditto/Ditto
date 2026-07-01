package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatCannotKickSelfException extends BusinessException {
    public ChatCannotKickSelfException() {
        super(ChatErrorCode.CHAT_CANNOT_KICK_SELF);
    }
}
