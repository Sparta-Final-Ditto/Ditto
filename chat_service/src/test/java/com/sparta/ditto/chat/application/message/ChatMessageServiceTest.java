package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageCursorResult;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageResult;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageVisibilityRange;
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
    private ChatMessageVisibilityService chatMessageVisibilityService;

    @BeforeEach
    void setUp() {
        chatMessageCommandPort = mock(ChatMessageCommandPort.class);
        chatMessageQueryPort = mock(ChatMessageQueryPort.class);
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatMessageVisibilityService = mock(ChatMessageVisibilityService.class);
        chatMessageService = new ChatMessageService(
                chatMessageCommandPort, chatMessageQueryPort,
                chatParticipantValidator, chatMessageVisibilityService);

        // 기존 조회 테스트는 활성 참여자 경로를 타도록 기본 stub (delete 테스트엔 안 쓰이므로 lenient)
        lenient().when(chatMessageVisibilityService.getVisibilityRange(any(), any()))
                .thenReturn(ChatMessageVisibilityRange.currentParticipant(
                        ROOM_ID, REQUESTER_ID, Instant.parse("2026-06-20T00:00:00Z")));
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
                    .given(chatMessageVisibilityService).getVisibilityRange(any(), any());

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
                    .given(chatMessageVisibilityService).getVisibilityRange(any(), any());

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

    @Nested
    @DisplayName("나간 사용자 조회 범위")
    class LeftUserVisibility {

        private static final Instant JOINED_AT = Instant.parse("2026-06-20T00:00:00Z");
        private static final String LAST_VISIBLE = "last-visible-id";
        private static final Instant LAST_VISIBLE_AT = Instant.parse("2026-06-21T00:00:00Z");

        private void givenLeft() {
            given(chatMessageVisibilityService.getVisibilityRange(ROOM_ID, REQUESTER_ID))
                    .willReturn(ChatMessageVisibilityRange.leftParticipant(
                            ROOM_ID, REQUESTER_ID, JOINED_AT, LAST_VISIBLE));
            given(chatMessageQueryPort.findByMessageIdAndRoomId(LAST_VISIBLE, ROOM_ID))
                    .willReturn(Optional.of(boundary(LAST_VISIBLE, LAST_VISIBLE_AT)));
        }

        @Test
        @DisplayName("볼 수 있는 메시지가 없으면(empty) 빈 결과를 반환하고 조회하지 않는다")
        void empty_returns_empty() {
            given(chatMessageVisibilityService.getVisibilityRange(ROOM_ID, REQUESTER_ID))
                    .willReturn(ChatMessageVisibilityRange.leftParticipant(
                            ROOM_ID, REQUESTER_ID, JOINED_AT, null)); // empty=true

            ChatMessageCursorResult result =
                    chatMessageService.getPreviousMessages(ROOM_ID, null, 30, REQUESTER_ID);

            assertThat(result.items()).isEmpty();
            verify(chatMessageQueryPort, never()).findLatestByRoomId(any(), anyInt());
            verify(chatMessageQueryPort, never())
                    .findLatestWithinRange(any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("before 없으면 joinedAt 하한 + lastVisible 상한으로 범위 조회한다")
        void latest_uses_join_and_visible_bounds() {
            givenLeft();
            given(chatMessageQueryPort.findLatestWithinRange(
                    eq(ROOM_ID), eq(JOINED_AT), eq(LAST_VISIBLE_AT), eq(LAST_VISIBLE), anyInt()))
                    .willReturn(List.of());

            chatMessageService.getPreviousMessages(ROOM_ID, null, 30, REQUESTER_ID);

            verify(chatMessageQueryPort).findLatestWithinRange(
                    eq(ROOM_ID), eq(JOINED_AT), eq(LAST_VISIBLE_AT), eq(LAST_VISIBLE), anyInt());
            verify(chatMessageQueryPort, never()).findLatestByRoomId(any(), anyInt());
        }

        @Test
        @DisplayName("missed 조회도 joinedAt 하한 + lastVisible 상한으로 묶는다")
        void missed_uses_bounds() {
            givenLeft();
            given(chatMessageQueryPort.findByMessageIdAndRoomId("after-id", ROOM_ID))
                    .willReturn(Optional.of(boundary("after-id", JOINED_AT)));

            chatMessageService.getMissedMessages(ROOM_ID, "after-id", 30, REQUESTER_ID);

            verify(chatMessageQueryPort).findAfterCursorWithinRange(
                    eq(ROOM_ID), eq(JOINED_AT), any(), eq("after-id"),
                    eq(LAST_VISIBLE_AT), eq(LAST_VISIBLE), anyInt());
        }

        private SentMessage boundary(String id, Instant at) {
            return new SentMessage(id, ROOM_ID, SENDER_ID, null, null,
                    MessageType.TEXT, "c", at, null);
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
