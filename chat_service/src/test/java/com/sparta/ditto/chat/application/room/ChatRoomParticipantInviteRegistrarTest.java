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

    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatRoomParticipantInviteRegistrar inviteRegistrar;

    @BeforeEach
    void setUp() {
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        inviteRegistrar = new ChatRoomParticipantInviteRegistrar(chatRoomParticipantPort);
    }

    @Test
    @DisplayName("신규 사용자는 MEMBER로 등록한다")
    void register_should_save_new_member() {
        // given
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        // when
        inviteRegistrar.register(ROOM_ID, List.of(TARGET_ID));

        // then
        ArgumentCaptor<ChatRoomParticipant> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantPort).save(captor.capture());
        ChatRoomParticipant saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(TARGET_ID);
        assertThat(saved.getRole()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(saved.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("나간 사용자는 기존 row를 재참여시킨다")
    void register_should_reinvite_left_participant() {
        // given
        ChatRoomParticipant leftParticipant =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        leftParticipant.leave("msg-1");
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(leftParticipant));

        // when
        inviteRegistrar.register(ROOM_ID, List.of(TARGET_ID));

        // then
        verify(chatRoomParticipantPort).save(leftParticipant);
        assertThat(leftParticipant.getLeftAt()).isNull();
        assertThat(leftParticipant.getRole()).isEqualTo(ParticipantRole.MEMBER);
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
        assertThatThrownBy(() -> inviteRegistrar.register(ROOM_ID, List.of(TARGET_ID)))
                .isInstanceOf(ChatAlreadyParticipantException.class);
        verify(chatRoomParticipantPort, never()).save(any());
    }
}
