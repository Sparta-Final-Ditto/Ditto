package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.application.room.port.ChatReadMessagePort;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatMessageNotFoundException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ChatReadService 테스트")
class ChatReadServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CURRENT_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final String NEXT_MESSAGE_ID =
            "018f7b7a-4d3d-7c22-9f1b-2a3c4d5e6f71";
    private static final String PREVIOUS_MESSAGE_ID =
            "018f7b7a-4d3b-7c22-9f1b-2a3c4d5e6f69";
    private static final Instant CURRENT_MESSAGE_CREATED_AT =
            Instant.parse("2026-06-22T10:00:00Z");
    private static final Instant NEXT_MESSAGE_CREATED_AT =
            Instant.parse("2026-06-22T10:01:00Z");
    private static final Instant PREVIOUS_MESSAGE_CREATED_AT =
            Instant.parse("2026-06-22T09:59:00Z");

    private ChatParticipantValidator chatParticipantValidator;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatReadMessagePort chatReadMessagePort;
    private ChatReadService chatReadService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatReadMessagePort = mock(ChatReadMessagePort.class);
        chatReadService = new ChatReadService(
                chatParticipantValidator,
                chatRoomParticipantPort,
                chatReadMessagePort
        );
    }

    @Test
    @DisplayName("처음 읽음 처리하면 읽은 위치와 unread를 atomic update로 갱신한다")
    void updateReadState_success_first_read() {
        // given
        ChatRoomParticipant participant = participant();
        givenParticipant(participant);
        givenMessage(NEXT_MESSAGE_ID, NEXT_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(NEXT_MESSAGE_ID));

        // then
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        verify(chatRoomParticipantPort).markReadAndResetUnread(
                eq(ROOM_ID), eq(REQUESTER_ID), eq(NEXT_MESSAGE_ID), any(Instant.class));
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.lastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
        assertThat(result.lastReadAt()).isNotNull();
    }

    @Test
    @DisplayName("기존 읽음 위치보다 최신 메시지이면 읽은 위치와 unread를 atomic update로 갱신한다")
    void updateReadState_success_update_to_newer_message() {
        // given
        ChatRoomParticipant participant = participant();
        participant.updateLastRead(CURRENT_MESSAGE_ID, Instant.now());
        ReflectionTestUtils.setField(participant, "unreadCount", 5L);
        givenParticipant(participant);
        givenMessage(CURRENT_MESSAGE_ID, CURRENT_MESSAGE_CREATED_AT);
        givenMessage(NEXT_MESSAGE_ID, NEXT_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(NEXT_MESSAGE_ID));

        // then
        verify(chatRoomParticipantPort).markReadAndResetUnread(
                eq(ROOM_ID), eq(REQUESTER_ID), eq(NEXT_MESSAGE_ID), any(Instant.class));
        assertThat(result.lastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
    }

    @Test
    @DisplayName("기존 읽음 위치보다 오래된 메시지이면 읽음 위치도 unread도 건드리지 않는다")
    void updateReadState_ignore_older_message() {
        // given
        ChatRoomParticipant participant = participant();
        participant.updateLastRead(CURRENT_MESSAGE_ID, Instant.now());
        ReflectionTestUtils.setField(participant, "unreadCount", 5L);
        givenParticipant(participant);
        givenMessage(CURRENT_MESSAGE_ID, CURRENT_MESSAGE_CREATED_AT);
        givenMessage(PREVIOUS_MESSAGE_ID, PREVIOUS_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(PREVIOUS_MESSAGE_ID));

        // then: 오래된 요청은 안 읽은 메시지를 유실시키지 않도록 unread를 그대로 둔다
        verify(chatRoomParticipantPort, never())
                .markReadAndResetUnread(any(), any(), any(), any());
        assertThat(participant.getUnreadCount()).isEqualTo(5L);
        assertThat(result.lastReadMessageId()).isEqualTo(CURRENT_MESSAGE_ID);
    }

    @Test
    @DisplayName("저장된 과거 읽음 메시지가 삭제/만료돼도 정상 갱신한다")
    void updateReadState_current_message_missing_still_updates() {
        // given
        ChatRoomParticipant participant = participant();
        participant.updateLastRead(CURRENT_MESSAGE_ID, Instant.now());
        givenParticipant(participant);
        // 저장돼 있던 과거 읽음 메시지가 Mongo에 더 이상 없음(삭제/만료)
        given(chatReadMessagePort.findReadMessage(ROOM_ID, CURRENT_MESSAGE_ID))
                .willReturn(Optional.empty());
        givenMessage(NEXT_MESSAGE_ID, NEXT_MESSAGE_CREATED_AT);

        // when
        ChatReadResult result = chatReadService.updateReadState(command(NEXT_MESSAGE_ID));

        // then: 과거 위치 부재는 오류가 아니라 "비교 불가" → 갱신 허용
        verify(chatRoomParticipantPort).markReadAndResetUnread(
                eq(ROOM_ID), eq(REQUESTER_ID), eq(NEXT_MESSAGE_ID), any(Instant.class));
        assertThat(result.lastReadMessageId()).isEqualTo(NEXT_MESSAGE_ID);
    }

    @Test
    @DisplayName("읽음 처리 기준 메시지가 없으면 메시지 없음 예외를 던진다")
    void updateReadState_fail_message_not_found() {
        // given
        ChatRoomParticipant participant = participant();
        givenParticipant(participant);
        given(chatReadMessagePort.findReadMessage(ROOM_ID, NEXT_MESSAGE_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(command(NEXT_MESSAGE_ID)))
                .isInstanceOf(ChatMessageNotFoundException.class);
    }

    @Test
    @DisplayName("현재 참여자가 아니면 읽음 상태를 갱신할 수 없다")
    void updateReadState_fail_not_participant() {
        // given
        given(chatRoomParticipantPort.findActiveParticipantForUpdate(ROOM_ID, REQUESTER_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(command(NEXT_MESSAGE_ID)))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 읽음 상태를 갱신할 수 없다")
    void updateReadState_fail_null_command() {
        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private ChatRoomParticipant participant() {
        return ChatRoomParticipant.join(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER
        );
    }

    private void givenParticipant(ChatRoomParticipant participant) {
        given(chatRoomParticipantPort.findActiveParticipantForUpdate(ROOM_ID, REQUESTER_ID))
                .willReturn(Optional.of(participant));
    }

    private void givenMessage(String messageId, Instant createdAt) {
        given(chatReadMessagePort.findReadMessage(ROOM_ID, messageId))
                .willReturn(Optional.of(new ChatReadMessagePort.ReadMessage(
                        messageId,
                        createdAt
                )));
    }

    private ChatReadCommand command(String lastReadMessageId) {
        return ChatReadCommand.of(REQUESTER_ID, ROOM_ID, lastReadMessageId);
    }
}
