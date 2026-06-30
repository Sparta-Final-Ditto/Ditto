package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatInviteForbiddenException extends BusinessException {

    public ChatInviteForbiddenException() {
        super(ChatErrorCode.CHAT_INVITE_FORBIDDEN);
    }
}
