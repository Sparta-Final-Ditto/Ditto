package com.sparta.ditto.chat.application.message.dto;

import com.sparta.ditto.chat.domain.message.MessageType;
import java.util.UUID;

public record ChatMessageSendCommand(
        UUID roomId,
        UUID senderId,
        UUID clientMessageId,
        MessageType messageType,
        String content
) {
}
