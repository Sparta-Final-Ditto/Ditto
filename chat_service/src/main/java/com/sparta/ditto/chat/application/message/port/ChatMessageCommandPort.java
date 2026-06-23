package com.sparta.ditto.chat.application.message.port;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.domain.message.MessageType;
import java.util.UUID;

public interface ChatMessageCommandPort {

    SentMessage saveUserMessage(
            String messageId,
            UUID roomId,
            UUID senderId,
            UUID clientMessageId,
            MessageType messageType,
            String content
    );

    SentMessage saveSystemMessage(
            String messageId,
            UUID roomId,
            UUID actorId,
            MessageType messageType,
            String content
    );

    void markDeleted(UUID roomId, String messageId);
}
