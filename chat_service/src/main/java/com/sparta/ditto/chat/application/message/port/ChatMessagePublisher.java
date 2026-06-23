package com.sparta.ditto.chat.application.message.port;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import java.util.UUID;

public interface ChatMessagePublisher {

    void ackToSender(UUID senderId, SentMessage message);

    void broadcast(UUID roomId, SentMessage message);
}
