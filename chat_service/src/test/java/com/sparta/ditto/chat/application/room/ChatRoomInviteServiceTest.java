package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.dto.command.ChatRoomInviteCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomInviteResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
import com.sparta.ditto.chat.domain.exception.ChatAlreadyParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatBlockedUserException;
import com.sparta.ditto.chat.domain.exception.ChatInviteForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatNotGroupRoomException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomInactiveException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatRoomInviteService 테스트")
class ChatRoomInviteServiceTest {

    private static final UUID OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TARGET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");

    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatUserValidationPort chatUserValidationPort;
    private ChatRoomInviteService chatRoomInviteService;

    @BeforeEach
    void setUp() {
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatUserValidationPort = mock(ChatUserValidationPort.class);
        chatRoomInviteService = new ChatRoomInviteService(
                chatRoomPort,
                chatRoomParticipantPort,
                chatUserValidationPort
        );
    }

    @Test
    @DisplayName("OWNER가 신규 사용자를 초대하면 MEMBER로 등록한다")
    void invite_should_register_new_member() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        // when
        ChatRoomInviteResult result = chatRoomInviteService.invite(command(TARGET_ID));

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.invitedUserIds()).containsExactly(TARGET_ID);
        verify(chatUserValidationPort).validateGroupChatParticipants(OWNER_ID, List.of(TARGET_ID));

        ArgumentCaptor<ChatRoomParticipant> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantPort).save(captor.capture());
        ChatRoomParticipant saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(TARGET_ID);
        assertThat(saved.getRole()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(saved.getLeftAt()).isNull();
    }

    @Test
    @DisplayName("나간 사용자를 재초대하면 기존 row를 재참여시킨다")
    void invite_should_reinvite_left_participant() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();
        ChatRoomParticipant leftParticipant =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        leftParticipant.leave("msg-1");
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(leftParticipant));

        // when
        chatRoomInviteService.invite(command(TARGET_ID));

        // then
        verify(chatRoomParticipantPort).save(leftParticipant);
        assertThat(leftParticipant.getLeftAt()).isNull();
        assertThat(leftParticipant.getRole()).isEqualTo(ParticipantRole.MEMBER);
    }

    @Test
    @DisplayName("이미 활성 참여자인 사용자를 초대하면 예외가 발생한다")
    void invite_should_reject_active_participant() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();
        ChatRoomParticipant activeParticipant =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findByRoomIdAndUserId(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(activeParticipant));

        // when & then
        assertThatThrownBy(() -> chatRoomInviteService.invite(command(TARGET_ID)))
                .isInstanceOf(ChatAlreadyParticipantException.class);
        verify(chatRoomParticipantPort, never()).save(any());
    }

    @Test
    @DisplayName("OWNER가 아닌 참여자는 초대할 수 없다")
    void invite_should_reject_non_owner() {
        // given
        givenActiveGroupRoom();
        ChatRoomParticipant member =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> chatRoomInviteService.invite(command(TARGET_ID)))
                .isInstanceOf(ChatInviteForbiddenException.class);
        verify(chatUserValidationPort, never()).validateGroupChatParticipants(any(), any());
    }

    @Test
    @DisplayName("요청자가 방의 활성 참여자가 아니면 초대할 수 없다")
    void invite_should_reject_non_participant_requester() {
        // given
        givenActiveGroupRoom();
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomInviteService.invite(command(TARGET_ID)))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("그룹방이 아니면 초대할 수 없다")
    void invite_should_reject_non_group_room() {
        // given
        ChatRoom directRoom = mock(ChatRoom.class);
        given(directRoom.getRoomType()).willReturn(RoomType.DIRECT);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(directRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomInviteService.invite(command(TARGET_ID)))
                .isInstanceOf(ChatNotGroupRoomException.class);
    }

    @Test
    @DisplayName("비활성 방에는 초대할 수 없다")
    void invite_should_reject_inactive_room() {
        // given
        ChatRoom inactiveRoom = mock(ChatRoom.class);
        given(inactiveRoom.getRoomType()).willReturn(RoomType.GROUP);
        given(inactiveRoom.getStatus()).willReturn(RoomStatus.INACTIVE);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(inactiveRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomInviteService.invite(command(TARGET_ID)))
                .isInstanceOf(ChatRoomInactiveException.class);
    }

    @Test
    @DisplayName("차단 관계인 사용자는 초대할 수 없다")
    void invite_should_reject_blocked_user() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();
        doThrow(new ChatBlockedUserException())
                .when(chatUserValidationPort)
                .validateGroupChatParticipants(OWNER_ID, List.of(TARGET_ID));

        // when & then
        assertThatThrownBy(() -> chatRoomInviteService.invite(command(TARGET_ID)))
                .isInstanceOf(ChatBlockedUserException.class);
        verify(chatRoomParticipantPort, never()).save(any());
    }

    private void givenActiveGroupRoom() {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getRoomType()).willReturn(RoomType.GROUP);
        given(chatRoom.getStatus()).willReturn(RoomStatus.ACTIVE);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
    }

    private void givenOwnerRequester() {
        ChatRoomParticipant owner =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.OWNER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));
    }

    private ChatRoomInviteCommand command(UUID targetUserId) {
        return ChatRoomInviteCommand.of(OWNER_ID, ROOM_ID, List.of(targetUserId));
    }
}
