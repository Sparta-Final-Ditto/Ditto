package com.sparta.ditto.chat.application.event;

import com.sparta.ditto.chat.application.message.dto.SentMessage;

public record ChatMessageNotificationRequestedEvent(SentMessage message) {
}
