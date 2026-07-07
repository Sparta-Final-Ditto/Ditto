package com.sparta.ditto.chat.presentation.dto.stomp;

import com.sparta.ditto.chat.domain.message.MessageType;
import java.util.UUID;

public record ChatMessageSendRequest(
        UUID clientMessageId,
        MessageType messageType,
        String content
) {
}
