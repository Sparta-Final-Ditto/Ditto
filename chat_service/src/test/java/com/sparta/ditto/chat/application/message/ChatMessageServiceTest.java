package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageCursorResult;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageResult;
import com.sparta.ditto.chat.application.message.port.ChatMessageCommandPort;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ChatMessageService 테스트")
class ChatMessageServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String CURSOR_ID = "cursor-message-id";

    private ChatMessageCommandPort chatMessageCommandPort;
    private ChatMessageQueryPort chatMessageQueryPort;
    private ChatParticipantValidator chatParticipantValidator;
    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        chatMessageCommandPort = mock(ChatMessageCommandPort.class);
        chatMessageQueryPort = mock(ChatMessageQueryPort.class);
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatMessageService = new ChatMessageService(
                chatMessageCommandPort, chatMessageQueryPort, chatParticipantValidator);
    }

    @Nested
    @DisplayName("getPreviousMessages()")
    class GetPreviousMessages {

        @Test
        @DisplayName("성공 - before 없으면 최신 페이지를 ASC로 반환한다")
        void success_latest_when_before_is_null() {
            // given
            given(chatMessageQueryPort.findLatestByRoomId(any(), anyInt()))
                    .willReturn(List.of(userMessage("m3"), userMessage("m2"), userMessage("m1")));

            // when
            ChatMessageCursorResult result =
                    chatMessageService.getPreviousMessages(ROOM_ID, null, 30, REQUESTER_ID);

            // then
            assertThat(result.items()).extracting(ChatMessageResult::messageId)
                    .containsExactly("m1", "m2", "m3");
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("성공 - before 조회 시 size+1 결과로 hasNext와 nextCursor를 계산한다")
        void success_before_with_hasNext() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.of(userMessage(CURSOR_ID)));
            given(chatMessageQueryPort.findBeforeCursor(any(), any(), any(), anyInt()))
                    .willReturn(List.of(userMessage("m5"), userMessage("m4"), userMessage("m3")));

            // when
            ChatMessageCursorResult result =
                    chatMessageService.getPreviousMessages(ROOM_ID, CURSOR_ID, 2, REQUESTER_ID);

            // then
            assertThat(result.items()).extracting(ChatMessageResult::messageId)
                    .containsExactly("m4", "m5");
            assertThat(result.hasNext()).isTrue();
            assertThat(result.nextCursor()).isEqualTo("m4");
        }

        @Test
        @DisplayName("실패 - 참여자가 아니면 조회 전에 예외가 발생한다")
        void fail_not_participant() {
            // given
            willThrow(new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                    .given(chatParticipantValidator).ensureActiveParticipant(any(), any());

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.getPreviousMessages(ROOM_ID, null, 30, REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
            verifyNoInteractions(chatMessageQueryPort, chatMessageCommandPort);
        }

        @Test
        @DisplayName("실패 - before cursor 메시지가 방에 없으면 예외가 발생한다")
        void fail_cursor_not_found() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.getPreviousMessages(ROOM_ID, CURSOR_ID, 30, REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("getMissedMessages()")
    class GetMissedMessages {

        @Test
        @DisplayName("성공 - after 이후 메시지를 ASC로 반환한다 (hasNext false)")
        void success_after_cursor() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.of(userMessage(CURSOR_ID)));
            given(chatMessageQueryPort.findAfterCursor(any(), any(), any(), anyInt()))
                    .willReturn(List.of(userMessage("m1"), userMessage("m2")));

            // when
            ChatMessageCursorResult result =
                    chatMessageService.getMissedMessages(ROOM_ID, CURSOR_ID, 2, REQUESTER_ID);

            // then
            assertThat(result.items()).extracting(ChatMessageResult::messageId)
                    .containsExactly("m1", "m2");
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("성공 - size+1 결과면 hasNext true, nextCursor는 가장 최신 messageId")
        void success_hasNext_boundary() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.of(userMessage(CURSOR_ID)));
            given(chatMessageQueryPort.findAfterCursor(any(), any(), any(), anyInt()))
                    .willReturn(List.of(userMessage("m1"), userMessage("m2"), userMessage("m3")));

            // when
            ChatMessageCursorResult result =
                    chatMessageService.getMissedMessages(ROOM_ID, CURSOR_ID, 2, REQUESTER_ID);

            // then
            assertThat(result.items()).extracting(ChatMessageResult::messageId)
                    .containsExactly("m1", "m2");
            assertThat(result.hasNext()).isTrue();
            assertThat(result.nextCursor()).isEqualTo("m2");
        }

        @Test
        @DisplayName("실패 - 참여자가 아니면 조회 전에 예외가 발생한다")
        void fail_not_participant() {
            // given
            willThrow(new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                    .given(chatParticipantValidator).ensureActiveParticipant(any(), any());

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.getMissedMessages(ROOM_ID, CURSOR_ID, 30, REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
            verifyNoInteractions(chatMessageQueryPort, chatMessageCommandPort);
        }

        @Test
        @DisplayName("실패 - after cursor 메시지가 방에 없으면 예외가 발생한다")
        void fail_cursor_not_found() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.getMissedMessages(ROOM_ID, CURSOR_ID, 30, REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("deleteMessage()")
    class DeleteMessage {

        @Test
        @DisplayName("성공 - 본인 메시지를 soft delete 한다")
        void success_soft_delete_own_message() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.of(myMessage("m1")));

            // when
            chatMessageService.deleteMessage(ROOM_ID, "m1", REQUESTER_ID);

            // then
            verify(chatMessageCommandPort).markDeleted(ROOM_ID, "m1");
        }

        @Test
        @DisplayName("멱등 - 이미 삭제된 메시지를 다시 삭제해도 예외 없이 처리된다")
        void idempotent_when_already_deleted() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.of(deletedMyMessage("m1")));

            // when
            chatMessageService.deleteMessage(ROOM_ID, "m1", REQUESTER_ID);

            // then
            verify(chatMessageCommandPort).markDeleted(ROOM_ID, "m1");
        }

        @Test
        @DisplayName("실패 - 본인이 보낸 메시지가 아니면 권한 예외가 발생한다")
        void fail_forbidden_when_not_sender() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.of(userMessage("m1")));

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.deleteMessage(ROOM_ID, "m1", REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_MESSAGE_FORBIDDEN);
            verify(chatMessageCommandPort, never()).markDeleted(any(), any());
        }

        @Test
        @DisplayName("실패 - 메시지가 방에 없으면 예외가 발생한다")
        void fail_message_not_found() {
            // given
            given(chatMessageQueryPort.findByMessageIdAndRoomId(any(), any()))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.deleteMessage(ROOM_ID, "m1", REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 참여자가 아니면 조회 전에 예외가 발생한다")
        void fail_not_participant() {
            // given
            willThrow(new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                    .given(chatParticipantValidator).ensureActiveParticipant(any(), any());

            // when & then
            assertThatThrownBy(() ->
                    chatMessageService.deleteMessage(ROOM_ID, "m1", REQUESTER_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT);
            verifyNoInteractions(chatMessageQueryPort, chatMessageCommandPort);
        }
    }

    private SentMessage userMessage(String messageId) {
        return message(messageId, SENDER_ID, null);
    }

    private SentMessage myMessage(String messageId) {
        return message(messageId, REQUESTER_ID, null);
    }

    private SentMessage deletedMyMessage(String messageId) {
        return message(messageId, REQUESTER_ID, Instant.now());
    }

    private SentMessage message(String messageId, UUID senderId, Instant deletedAt) {
        return new SentMessage(
                messageId,
                ROOM_ID,
                senderId,
                null,
                UUID.randomUUID(),
                MessageType.TEXT,
                "c-" + messageId,
                Instant.now(),
                deletedAt
        );
    }
}
