package com.sparta.ditto.chat.application.room.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChatReadMessagePort {

    Optional<ReadMessage> findReadMessage(UUID roomId, String messageId);

    record ReadMessage(
            String messageId,
            Instant createdAt
    ) {
    }
}
