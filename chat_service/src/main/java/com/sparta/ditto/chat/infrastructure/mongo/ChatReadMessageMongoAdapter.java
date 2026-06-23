package com.sparta.ditto.chat.infrastructure.mongo;

import com.sparta.ditto.chat.application.room.port.ChatReadMessagePort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatReadMessageMongoAdapter implements ChatReadMessagePort {

    private final ChatMessageMongoRepository chatMessageMongoRepository;

    @Override
    public Optional<ReadMessage> findReadMessage(UUID roomId, String messageId) {
        return chatMessageMongoRepository.findByMessageIdAndRoomId(messageId, roomId)
                .map(message -> new ReadMessage(
                        message.getMessageId(),
                        message.getCreatedAt()
                ));
    }
}
