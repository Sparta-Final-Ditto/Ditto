package com.sparta.ditto.chat.infrastructure.mongo;

import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Document(collection = "chat_messages")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_chat_messages_room_id_sender_id_client_message_id",
                def = "{'room_id': 1, 'sender_id': 1, 'client_message_id': 1}",
                unique = true,
                sparse = true
        ),
        @CompoundIndex(
                name = "idx_chat_messages_room_id_created_at_message_id",
                def = "{'room_id': 1, 'created_at': 1, '_id': 1}"
        )
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageDocument {

    @Id
    private String messageId;

    @Field("room_id")
    private UUID roomId;

    @Field("sender_id")
    private UUID senderId;

    @Field("actor_id")
    private UUID actorId;

    @Field("client_message_id")
    private UUID clientMessageId;

    @Field("message_type")
    private MessageType messageType;

    private String content;

    @Field("created_at")
    private Instant createdAt;

    @Field("deleted_at")
    private Instant deletedAt;

    private ChatMessageDocument(
            String messageId,
            UUID roomId,
            UUID senderId,
            UUID actorId,
            UUID clientMessageId,
            MessageType messageType,
            String content
    ) {
        this.messageId = Objects.requireNonNull(messageId, "messageId는 null일 수 없습니다.");
        this.roomId = Objects.requireNonNull(roomId, "roomId는 null일 수 없습니다.");
        this.senderId = senderId;
        this.actorId = actorId;
        this.clientMessageId = clientMessageId;
        this.messageType = Objects.requireNonNull(messageType, "messageType은 null일 수 없습니다.");
        this.content = Objects.requireNonNull(content, "content는 null일 수 없습니다.");
        this.createdAt = Instant.now();
    }

    public static ChatMessageDocument createUserMessage(
            String messageId,
            UUID roomId,
            UUID senderId,
            UUID clientMessageId,
            MessageType messageType,
            String content
    ) {
        Objects.requireNonNull(senderId, "사용자 메시지의 senderId는 null일 수 없습니다.");
        Objects.requireNonNull(clientMessageId, "사용자 메시지의 clientMessageId는 null일 수 없습니다.");
        return new ChatMessageDocument(messageId, roomId, senderId, null, clientMessageId, messageType, content);
    }

    public static ChatMessageDocument createSystemMessage(
            String messageId,
            UUID roomId,
            UUID actorId,
            MessageType messageType,
            String content
    ) {
        Objects.requireNonNull(actorId, "시스템 메시지의 actorId는 null일 수 없습니다.");
        return new ChatMessageDocument(messageId, roomId, null, actorId, null, messageType, content);
    }

    public void markDeleted() {
        if (this.deletedAt != null) {
            return; // 이미 삭제됨
        }
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}