package com.sparta.ditto.chat.infrastructure.mongo;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessageCommandPort;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageMongoAdapter implements ChatMessageCommandPort, ChatMessageQueryPort {

    private static final String COLLECTION = "chat_messages";
    private static final String FIELD_ROOM_ID = "room_id";
    private static final String FIELD_SENDER_ID = "sender_id";
    private static final String FIELD_DELETED_AT = "deleted_at";
    private static final String FIELD_MESSAGE_TYPE = "message_type";
    private static final String FIELD_CREATED_AT = "created_at";

    private final ChatMessageMongoRepository chatMessageMongoRepository;
    private final MongoTemplate mongoTemplate;

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

    @Override
    public Map<UUID, Long> countUnreadBatch(
            Map<UUID, String> lastReadMessageIdByRoomId, UUID myUserId) {
        if (lastReadMessageIdByRoomId == null || lastReadMessageIdByRoomId.isEmpty()) {
            return Map.of();
        }

        List<UUID> noReadRooms = new ArrayList<>();
        Map<UUID, String> hasReadRooms = new HashMap<>();

        for (Map.Entry<UUID, String> entry : lastReadMessageIdByRoomId.entrySet()) {
            String lastRead = entry.getValue();
            if (lastRead == null || lastRead.isBlank()) {
                noReadRooms.add(entry.getKey());
            } else {
                hasReadRooms.put(entry.getKey(), lastRead);
            }
        }

        Map<UUID, Long> result = new HashMap<>();
        if (!noReadRooms.isEmpty()) {
            result.putAll(aggregateUnreadAll(noReadRooms, myUserId));
        }
        if (!hasReadRooms.isEmpty()) {
            result.putAll(aggregateUnreadAfterCursor(hasReadRooms, myUserId));
        }
        return result;
    }

    private Map<UUID, Long> aggregateUnreadAll(List<UUID> roomIds, UUID myUserId) {
        Criteria criteria = Criteria.where(FIELD_ROOM_ID).in(roomIds)
                .and(FIELD_SENDER_ID).ne(myUserId)
                .and(FIELD_DELETED_AT).isNull()
                .and(FIELD_MESSAGE_TYPE).in(
                        MessageType.TEXT.name(), MessageType.IMAGE.name());

        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group(FIELD_ROOM_ID).count().as("count")
        );

        return mongoTemplate.aggregate(agg, COLLECTION, UnreadCountResult.class)
                .getMappedResults()
                .stream()
                .collect(Collectors.toMap(
                        UnreadCountResult::getRoomId, UnreadCountResult::getCount));
    }

    private Map<UUID, Long> aggregateUnreadAfterCursor(
            Map<UUID, String> hasReadRooms, UUID myUserId) {
        Map<String, ChatMessageDocument> cursorById =
                chatMessageMongoRepository.findByMessageIdIn(hasReadRooms.values())
                        .stream()
                        .collect(Collectors.toMap(
                                ChatMessageDocument::getMessageId, Function.identity()));

        List<UUID> fallbackRooms = new ArrayList<>();
        List<Criteria> roomConditions = new ArrayList<>();

        for (Map.Entry<UUID, String> entry : hasReadRooms.entrySet()) {
            UUID roomId = entry.getKey();
            ChatMessageDocument cursor = cursorById.get(entry.getValue());
            if (cursor == null) {
                fallbackRooms.add(roomId);
            } else {
                Criteria afterCursor = new Criteria().orOperator(
                        Criteria.where(FIELD_CREATED_AT).gt(cursor.getCreatedAt()),
                        new Criteria().andOperator(
                                Criteria.where(FIELD_CREATED_AT).is(cursor.getCreatedAt()),
                                Criteria.where("_id").gt(cursor.getMessageId())
                        )
                );
                roomConditions.add(new Criteria().andOperator(
                        Criteria.where(FIELD_ROOM_ID).is(roomId),
                        afterCursor
                ));
            }
        }

        Map<UUID, Long> result = new HashMap<>();

        if (!fallbackRooms.isEmpty()) {
            result.putAll(aggregateUnreadAll(fallbackRooms, myUserId));
        }

        if (!roomConditions.isEmpty()) {
            Criteria criteria = new Criteria().andOperator(
                    new Criteria().orOperator(roomConditions.toArray(new Criteria[0])),
                    Criteria.where(FIELD_SENDER_ID).ne(myUserId),
                    Criteria.where(FIELD_DELETED_AT).isNull(),
                    Criteria.where(FIELD_MESSAGE_TYPE).in(
                            MessageType.TEXT.name(), MessageType.IMAGE.name())
            );

            Aggregation agg = Aggregation.newAggregation(
                    Aggregation.match(criteria),
                    Aggregation.group(FIELD_ROOM_ID).count().as("count")
            );

            mongoTemplate.aggregate(agg, COLLECTION, UnreadCountResult.class)
                    .getMappedResults()
                    .forEach(r -> result.put(r.getRoomId(), r.getCount()));
        }

        return result;
    }

    @Getter
    @NoArgsConstructor
    private static class UnreadCountResult {
        @Id
        private UUID roomId;
        private long count;
    }
}
