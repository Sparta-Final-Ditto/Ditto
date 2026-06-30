package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.dto.command.ChatRoomInviteCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomInviteResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
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
import org.mockito.InOrder;

@DisplayName("ChatRoomInviteService 테스트")
class ChatRoomInviteServiceTest {

    private static final UUID OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");

    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatUserValidationPort chatUserValidationPort;
    private ChatRoomParticipantInviteRegistrar inviteRegistrar;
    private ChatRoomInviteService chatRoomInviteService;
    private ChatRoom activeRoom;

    @BeforeEach
    void setUp() {
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatUserValidationPort = mock(ChatUserValidationPort.class);
        inviteRegistrar = mock(ChatRoomParticipantInviteRegistrar.class);
        chatRoomInviteService = new ChatRoomInviteService(
                chatRoomPort,
                chatRoomParticipantPort,
                chatUserValidationPort,
                inviteRegistrar
        );
    }

    @Test
    @DisplayName("OWNER 초대 시 외부 검증을 먼저 끝낸 뒤 registrar에 등록을 위임한다")
    void invite_should_validate_then_delegate_registration() {
        // given
        givenActiveGroupRoom();
        givenOwnerRequester();

        // when
        ChatRoomInviteResult result = chatRoomInviteService.invite(command(TARGET_ID));

        // then
        org.assertj.core.api.Assertions.assertThat(result.roomId()).isEqualTo(ROOM_ID);
        org.assertj.core.api.Assertions.assertThat(result.invitedUserIds())
                .containsExactly(TARGET_ID);

        // 외부 검증이 row 변경(registrar)보다 먼저 실행되어야 한다.
        InOrder order = inOrder(chatUserValidationPort, inviteRegistrar);
        order.verify(chatUserValidationPort)
                .validateGroupChatParticipants(OWNER_ID, List.of(TARGET_ID));
        order.verify(inviteRegistrar).register(activeRoom, List.of(TARGET_ID));
    }

    @Test
    @DisplayName("OWNER가 아닌 참여자는 초대할 수 없고 등록도 시도하지 않는다")
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
        verify(inviteRegistrar, never()).register(any(), any());
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
        verify(inviteRegistrar, never()).register(any(), any());
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
        verify(inviteRegistrar, never()).register(any(), any());
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
        verify(inviteRegistrar, never()).register(any(), any());
    }

    @Test
    @DisplayName("차단 관계인 사용자는 초대할 수 없고 등록도 시도하지 않는다")
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
        verify(inviteRegistrar, never()).register(any(), any());
    }

    private void givenActiveGroupRoom() {
        activeRoom = mock(ChatRoom.class);
        given(activeRoom.getRoomType()).willReturn(RoomType.GROUP);
        given(activeRoom.getStatus()).willReturn(RoomStatus.ACTIVE);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(activeRoom));
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
