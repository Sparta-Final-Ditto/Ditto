package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatKickForbiddenException extends BusinessException {
    public ChatKickForbiddenException() {
        super(ChatErrorCode.CHAT_KICK_FORBIDDEN);
    }
}
