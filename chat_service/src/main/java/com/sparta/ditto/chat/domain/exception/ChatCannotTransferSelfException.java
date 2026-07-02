package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatCannotTransferSelfException extends BusinessException {
    public ChatCannotTransferSelfException() {
        super(ChatErrorCode.CHAT_CANNOT_TRANSFER_SELF);
    }
}
