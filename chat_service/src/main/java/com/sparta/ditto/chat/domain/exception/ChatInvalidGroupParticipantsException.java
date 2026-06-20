package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatInvalidGroupParticipantsException extends BusinessException {

    public ChatInvalidGroupParticipantsException() {
        super(ChatErrorCode.CHAT_INVALID_GROUP_PARTICIPANTS);
    }
}
