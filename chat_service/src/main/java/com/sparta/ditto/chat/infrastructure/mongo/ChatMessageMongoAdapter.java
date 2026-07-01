package com.sparta.ditto.chat.infrastructure.mongo;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessageCommandPort;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageMongoAdapter implements ChatMessageCommandPort, ChatMessageQueryPort {

    private final ChatMessageMongoRepository chatMessageMongoRepository;

    @Override
    public SentMessage saveUserMessage(
            String messageId,
            UUID roomId,
            UUID senderId,
            UUID clientMessageId,
            MessageType messageType,
            String content
    ) {
        ChatMessageDocument saved = chatMessageMongoRepository.save(
                ChatMessageDocument.createUserMessage(
                        messageId,
                        roomId,
                        senderId,
                        clientMessageId,
                        messageType,
                        content
                )
        );
        return toSentMessage(saved);
    }

    @Override
    public SentMessage saveSystemMessage(
            String messageId,
            UUID roomId,
            UUID actorId,
            MessageType messageType,
            String content
    ) {
        ChatMessageDocument saved = chatMessageMongoRepository.save(
                ChatMessageDocument.createSystemMessage(
                        messageId,
                        roomId,
                        actorId,
                        messageType,
                        content
                )
        );
        return toSentMessage(saved);
    }

    @Override
    public void markDeleted(UUID roomId, String messageId) {
        chatMessageMongoRepository.markDeletedByMessageIdAndRoomId(
                messageId, roomId, Instant.now());
    }

    @Override
    public Optional<SentMessage> findByMessageId(String messageId) {
        return chatMessageMongoRepository.findByMessageId(messageId)
                .map(this::toSentMessage);
    }

    @Override
    public Optional<SentMessage> findByMessageIdAndRoomId(String messageId, UUID roomId) {
        return chatMessageMongoRepository.findByMessageIdAndRoomId(messageId, roomId)
                .map(this::toSentMessage);
    }

    @Override
    public List<SentMessage> findLatestByRoomId(UUID roomId, int limit) {
        return chatMessageMongoRepository.findLatestByRoomId(roomId, Limit.of(limit))
                .stream()
                .map(this::toSentMessage)
                .toList();
    }

    @Override
    public List<SentMessage> findBeforeCursor(
            UUID roomId,
            Instant cursorCreatedAt,
            String cursorMessageId,
            int limit
    ) {
        return chatMessageMongoRepository.findBeforeCursor(
                        roomId,
                        cursorCreatedAt,
                        cursorMessageId,
                        Limit.of(limit)
                )
                .stream()
                .map(this::toSentMessage)
                .toList();
    }

    @Override
    public List<SentMessage> findAfterCursor(
            UUID roomId,
            Instant cursorCreatedAt,
            String cursorMessageId,
            int limit
    ) {
        return chatMessageMongoRepository.findAfterCursor(
                        roomId,
                        cursorCreatedAt,
                        cursorMessageId,
                        Limit.of(limit)
                )
                .stream()
                .map(this::toSentMessage)
                .toList();
    }

    private SentMessage toSentMessage(ChatMessageDocument document) {
        return new SentMessage(
                document.getMessageId(),
                document.getRoomId(),
                document.getSenderId(),
                document.getActorId(),
                document.getClientMessageId(),
                document.getMessageType(),
                document.getContent(),
                document.getCreatedAt(),
                document.getDeletedAt()
        );
    }

    @Override
    public List<SentMessage> findLatestWithinRange(
            UUID roomId, Instant joinedAt,
            Instant upperCreatedAt, String upperMessageId, int limit) {
        return chatMessageMongoRepository.findLatestWithinRange(
                        roomId, joinedAt, upperCreatedAt, upperMessageId, Limit.of(limit))
                .stream().map(this::toSentMessage).toList();
    }

    @Override
    public List<SentMessage> findBeforeCursorWithinRange(
            UUID roomId, Instant joinedAt,
            Instant cursorCreatedAt, String cursorMessageId, int limit) {
        return chatMessageMongoRepository.findBeforeCursorWithinRange(
                        roomId, joinedAt, cursorCreatedAt, cursorMessageId, Limit.of(limit))
                .stream().map(this::toSentMessage).toList();
    }

    @Override
    public List<SentMessage> findAfterCursorWithinRange(
            UUID roomId, Instant joinedAt,
            Instant afterCreatedAt, String afterMessageId,
            Instant upperCreatedAt, String upperMessageId, int limit) {
        return chatMessageMongoRepository.findAfterCursorWithinRange(
                        roomId, joinedAt, afterCreatedAt, afterMessageId,
                        upperCreatedAt, upperMessageId, Limit.of(limit))
                .stream().map(this::toSentMessage).toList();
    }

    @Override
    public List<SentMessage> findByMessageIds(Collection<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }
        return chatMessageMongoRepository.findByMessageIdIn(messageIds)
                .stream()
                .map(this::toSentMessage)
                .toList();
    }
}
