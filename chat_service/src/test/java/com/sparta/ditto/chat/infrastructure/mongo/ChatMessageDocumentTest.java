package com.sparta.ditto.chat.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.ditto.chat.domain.message.MessageType;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ChatMessageDocument 테스트")
class ChatMessageDocumentTest {

    private final String messageId = UUID.randomUUID().toString();
    private final UUID roomId = UUID.randomUUID();
    private final UUID senderId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID clientMessageId = UUID.randomUUID();

    @Nested
    @DisplayName("createUserMessage()")
    class CreateUserMessage {

        @Test
        @DisplayName("성공 - 사용자 메시지 생성")
        void success() {
            // when
            ChatMessageDocument doc = ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, clientMessageId, MessageType.TEXT, "hello");

            // then
            assertThat(doc.getMessageId()).isEqualTo(messageId);
            assertThat(doc.getRoomId()).isEqualTo(roomId);
            assertThat(doc.getSenderId()).isEqualTo(senderId);
            assertThat(doc.getClientMessageId()).isEqualTo(clientMessageId);
            assertThat(doc.getActorId()).isNull();
            assertThat(doc.getMessageType()).isEqualTo(MessageType.TEXT);
            assertThat(doc.getContent()).isEqualTo("hello");
            assertThat(doc.getCreatedAt()).isNotNull();
            assertThat(doc.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("실패 - senderId가 null")
        void fail_sender_null() {
            assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                    messageId, roomId, null, clientMessageId, MessageType.TEXT, "hello"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("실패 - clientMessageId가 null")
        void fail_client_message_id_null() {
            assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, null, MessageType.TEXT, "hello"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("실패 - 필수 공통 값이 null (messageId/roomId/messageType/content)")
        void fail_required_null() {
            assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                    null, roomId, senderId, clientMessageId, MessageType.TEXT, "hello"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                    messageId, null, senderId, clientMessageId, MessageType.TEXT, "hello"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, clientMessageId, null, "hello"))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, clientMessageId, MessageType.TEXT, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("createSystemMessage()")
    class CreateSystemMessage {

        @Test
        @DisplayName("성공 - 시스템 메시지 생성")
        void success() {
            // when
            ChatMessageDocument doc = ChatMessageDocument.createSystemMessage(
                    messageId, roomId, actorId, MessageType.SYSTEM_JOIN, "joined");

            // then
            assertThat(doc.getActorId()).isEqualTo(actorId);
            assertThat(doc.getSenderId()).isNull();
            assertThat(doc.getClientMessageId()).isNull();
            assertThat(doc.getMessageType()).isEqualTo(MessageType.SYSTEM_JOIN);
            assertThat(doc.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패 - actorId가 null")
        void fail_actor_null() {
            assertThatThrownBy(() -> ChatMessageDocument.createSystemMessage(
                    messageId, roomId, null, MessageType.SYSTEM_JOIN, "joined"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("markDeleted() / isDeleted()")
    class SoftDelete {

        @Test
        @DisplayName("초기 상태는 삭제되지 않음")
        void not_deleted_initially() {
            ChatMessageDocument doc = ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, clientMessageId, MessageType.TEXT, "hello");

            assertThat(doc.isDeleted()).isFalse();
            assertThat(doc.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("성공 - markDeleted 호출 시 deletedAt 설정")
        void mark_deleted() {
            ChatMessageDocument doc = ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, clientMessageId, MessageType.TEXT, "hello");

            doc.markDeleted();

            assertThat(doc.isDeleted()).isTrue();
            assertThat(doc.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("멱등 - 이미 삭제된 메시지는 deletedAt이 갱신되지 않음")
        void mark_deleted_idempotent() {
            ChatMessageDocument doc = ChatMessageDocument.createUserMessage(
                    messageId, roomId, senderId, clientMessageId, MessageType.TEXT, "hello");

            doc.markDeleted();
            var firstDeletedAt = doc.getDeletedAt();
            doc.markDeleted();

            assertThat(doc.getDeletedAt()).isEqualTo(firstDeletedAt);
        }
    }

    @Test
    @DisplayName("실패 - 사용자 메시지에 SYSTEM_* 타입 사용")
    void fail_user_message_with_system_type() {
        assertThatThrownBy(() -> ChatMessageDocument.createUserMessage(
                messageId, roomId, senderId, clientMessageId, MessageType.SYSTEM_JOIN, "hello"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("실패 - 시스템 메시지에 사용자(TEXT/IMAGE) 타입 사용")
    void fail_system_message_with_user_type() {
        assertThatThrownBy(() -> ChatMessageDocument.createSystemMessage(
                messageId, roomId, actorId, MessageType.TEXT, "joined"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}