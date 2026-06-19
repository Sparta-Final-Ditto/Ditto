package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatNotParticipantException extends BusinessException {

    public ChatNotParticipantException() {
        super(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }
}
