package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatMessageSaveFailedException extends BusinessException {

    public ChatMessageSaveFailedException() {
        super(ChatErrorCode.CHAT_MESSAGE_SAVE_FAILED);
    }
}
