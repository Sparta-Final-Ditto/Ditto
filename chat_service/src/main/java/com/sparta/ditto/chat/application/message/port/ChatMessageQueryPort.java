package com.sparta.ditto.chat.application.message.port;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageQueryPort {

    Optional<SentMessage> findByMessageId(String messageId);

    Optional<SentMessage> findByMessageIdAndRoomId(String messageId, UUID roomId);

    List<SentMessage> findLatestByRoomId(UUID roomId, int limit);

    List<SentMessage> findBeforeCursor(
            UUID roomId,
            Instant cursorCreatedAt,
            String cursorMessageId,
            int limit
    );

    List<SentMessage> findAfterCursor(
            UUID roomId,
            Instant cursorCreatedAt,
            String cursorMessageId,
            int limit
    );
}
