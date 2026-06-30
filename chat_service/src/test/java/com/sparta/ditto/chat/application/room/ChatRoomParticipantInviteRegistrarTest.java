package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatAlreadyParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatRoomParticipantInviteRegistrar 테스트")
class ChatRoomParticipantInviteRegistrarTest {

    private static final UUID TARGET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final String LAST_MESSAGE_ID = "msg-100";
    private static final Instant LAST_MESSAGE_AT = Instant.parse("2026-06-29T00:00:00Z");

    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatRoomParticipantInviteRegistrar inviteRegistrar;

    @BeforeEach
    void setUp() {
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        inviteRegistrar = new ChatRoomParticipantInviteRegistrar(chatRoomParticipantPort);
    }

    @Test
    @DisplayName("신규 사용자는 MEMBER로 등록하고 읽음 기준을 현재 마지막 메시지로 맞춘다")
    void register_should_save_new_member_with_read_baseline() {
        // given
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        // when
        inviteRegistrar.register(roomWithLastMessage(), List.of(TARGET_ID));

        // then
        ArgumentCaptor<ChatRoomParticipant> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantPort).save(captor.capture());
        ChatRoomParticipant saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(TARGET_ID);
        assertThat(saved.getRole()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(saved.getLeftAt()).isNull();
        assertThat(saved.getLastReadMessageId()).isEqualTo(LAST_MESSAGE_ID);
        assertThat(saved.getLastReadAt()).isEqualTo(LAST_MESSAGE_AT);
    }

    @Test
    @DisplayName("나간 사용자는 기존 row를 재참여시키고 이전 읽음 기준을 현재 메시지로 갱신한다")
    void register_should_reinvite_left_participant_and_reset_read_state() {
        // given
        ChatRoomParticipant leftParticipant =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        leftParticipant.updateLastRead("old-msg", Instant.parse("2026-06-01T00:00:00Z"));
        leftParticipant.leave("old-msg");
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(leftParticipant));

        // when
        inviteRegistrar.register(roomWithLastMessage(), List.of(TARGET_ID));

        // then
        verify(chatRoomParticipantPort).save(leftParticipant);
        assertThat(leftParticipant.getLeftAt()).isNull();
        assertThat(leftParticipant.getRole()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(leftParticipant.getLastReadMessageId()).isEqualTo(LAST_MESSAGE_ID);
        assertThat(leftParticipant.getLastReadAt()).isEqualTo(LAST_MESSAGE_AT);
        assertThat(leftParticipant.getLastVisibleMessageId()).isNull();
    }

    @Test
    @DisplayName("메시지가 없는 방에 초대하면 읽음 기준은 비어 있다")
    void register_should_leave_read_state_empty_when_room_has_no_message() {
        // given
        ChatRoom emptyRoom = mock(ChatRoom.class);
        given(emptyRoom.getId()).willReturn(ROOM_ID);
        given(emptyRoom.getLastMessageId()).willReturn(null);
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        // when
        inviteRegistrar.register(emptyRoom, List.of(TARGET_ID));

        // then
        ArgumentCaptor<ChatRoomParticipant> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantPort).save(captor.capture());
        assertThat(captor.getValue().getLastReadMessageId()).isNull();
    }

    @Test
    @DisplayName("이미 활성 참여자인 사용자를 초대하면 예외가 발생한다")
    void register_should_reject_active_participant() {
        // given
        ChatRoomParticipant activeParticipant =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(activeParticipant));

        // when & then
        assertThatThrownBy(() ->
                inviteRegistrar.register(roomWithLastMessage(), List.of(TARGET_ID)))
                .isInstanceOf(ChatAlreadyParticipantException.class);
        verify(chatRoomParticipantPort, never()).save(any());
    }

    private ChatRoom roomWithLastMessage() {
        ChatRoom room = mock(ChatRoom.class);
        given(room.getId()).willReturn(ROOM_ID);
        given(room.getLastMessageId()).willReturn(LAST_MESSAGE_ID);
        given(room.getLastMessageAt()).willReturn(LAST_MESSAGE_AT);
        return room;
    }
}
