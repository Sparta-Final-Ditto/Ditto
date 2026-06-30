package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatAlreadyParticipantException extends BusinessException {

    public ChatAlreadyParticipantException() {
        super(ChatErrorCode.CHAT_ALREADY_PARTICIPANT);
    }
}
