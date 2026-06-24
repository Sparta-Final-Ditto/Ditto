package com.sparta.ditto.chat.application.event;

public interface ChatNotificationEventPublisher {

    void publish(ChatMessageCreatedEvent event);
}
