package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatUnsupportedRoleChangeException extends BusinessException {
    public ChatUnsupportedRoleChangeException() {
        super(ChatErrorCode.CHAT_UNSUPPORTED_ROLE_CHANGE);
    }
}
