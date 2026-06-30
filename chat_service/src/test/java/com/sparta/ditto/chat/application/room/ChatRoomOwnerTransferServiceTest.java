package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.sparta.ditto.chat.application.room.dto.result.ChatRoomOwnerTransferResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoleChangeForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatUnsupportedRoleChangeException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatRoomOwnerTransferService 테스트")
class ChatRoomOwnerTransferServiceTest {

    private static final UUID OWNER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");

    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatRoomOwnerTransferService chatRoomOwnerTransferService;

    @BeforeEach
    void setUp() {
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatRoomOwnerTransferService = new ChatRoomOwnerTransferService(
                chatRoomPort, chatRoomParticipantPort);
    }

    @Test
    @DisplayName("OWNER가 권한을 위임하면 기존 OWNER는 MEMBER로 강등되고 대상이 OWNER가 된다")
    void transfer_should_swap_roles() {
        // given
        givenActiveGroupRoom();
        ChatRoomParticipant owner =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.OWNER);
        ChatRoomParticipant target =
                ChatRoomParticipant.join(ROOM_ID, TARGET_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, TARGET_ID))
                .willReturn(Optional.of(target));

        // when
        ChatRoomOwnerTransferResult result = chatRoomOwnerTransferService.transferOwner(
                OWNER_ID, ROOM_ID, TARGET_ID, ParticipantRole.OWNER);

        // then
        assertThat(result.newOwnerId()).isEqualTo(TARGET_ID);
        assertThat(result.previousOwnerId()).isEqualTo(OWNER_ID);
        assertThat(owner.getRole()).isEqualTo(ParticipantRole.MEMBER);
        assertThat(target.getRole()).isEqualTo(ParticipantRole.OWNER);
    }

    @Test
    @DisplayName("OWNER가 아니면 권한을 위임할 수 없다")
    void transfer_should_reject_non_owner() {
        // given
        givenActiveGroupRoom();
        ChatRoomParticipant member =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.MEMBER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(member));

        // when & then
        assertThatThrownBy(() -> chatRoomOwnerTransferService.transferOwner(
                OWNER_ID, ROOM_ID, TARGET_ID, ParticipantRole.OWNER))
                .isInstanceOf(ChatRoleChangeForbiddenException.class);
    }

    @Test
    @DisplayName("대상이 활성 참여자가 아니면 위임할 수 없다")
    void transfer_should_reject_non_participant_target() {
        // given
        givenActiveGroupRoom();
        ChatRoomParticipant owner =
                ChatRoomParticipant.join(ROOM_ID, OWNER_ID, ParticipantRole.OWNER);
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, OWNER_ID))
                .willReturn(Optional.of(owner));
        given(chatRoomParticipantPort.findActiveParticipant(ROOM_ID, TARGET_ID))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomOwnerTransferService.transferOwner(
                OWNER_ID, ROOM_ID, TARGET_ID, ParticipantRole.OWNER))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("OWNER 위임 외의 권한 변경은 지원하지 않는다")
    void transfer_should_reject_unsupported_role() {
        assertThatThrownBy(() -> chatRoomOwnerTransferService.transferOwner(
                OWNER_ID, ROOM_ID, TARGET_ID, ParticipantRole.MEMBER))
                .isInstanceOf(ChatUnsupportedRoleChangeException.class);
    }

    private void givenActiveGroupRoom() {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getRoomType()).willReturn(RoomType.GROUP);
        given(chatRoom.getStatus()).willReturn(RoomStatus.ACTIVE);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
    }
}
