package com.sparta.ditto.chat.application.event;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import java.util.UUID;

public record ChatSystemMessageBroadcastRequestedEvent(UUID roomId, SentMessage message) {
}
