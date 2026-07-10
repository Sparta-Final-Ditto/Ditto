package com.sparta.ditto.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.application.NotificationEventHandler;
import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.infrastructure.kafka.dto.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationEventHandler notificationEventHandler;

    @KafkaListener(topics = "chat-message-created")
    public void consume(String message) throws Exception {
        ChatMessageEvent event = objectMapper.readValue(message, ChatMessageEvent.class);
        ChatNotificationCommand cmd = ChatNotificationCommand.of(
                event.messageId(),
                event.senderId(),
                event.senderNickname(),
                event.senderProfileImageUrl(),
                event.roomId(),
                event.receiverIds(),
                event.preview()
        );
        notificationEventHandler.handleChatMessage(cmd);
    }
}
