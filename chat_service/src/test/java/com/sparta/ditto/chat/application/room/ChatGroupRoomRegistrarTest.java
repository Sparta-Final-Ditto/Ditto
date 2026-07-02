package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.room.dto.result.ChatGroupRoomResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatGroupRoomRegistrar 테스트")
class ChatGroupRoomRegistrarTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final String ROOM_NAME = "스터디 그룹";

    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatMessageSendService chatMessageSendService;
    private ChatGroupRoomRegistrar groupRoomRegistrar;

    @BeforeEach
    void setUp() {
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatMessageSendService = mock(ChatMessageSendService.class);
        groupRoomRegistrar = new ChatGroupRoomRegistrar(
                chatRoomPort, chatRoomParticipantPort, chatMessageSendService);
    }

    @Test
    @DisplayName("방·OWNER/MEMBER 참여자·생성 시스템 메시지를 저장한다")
    void create_should_persist_room_participants_and_system_message() {
        // given
        ChatRoom savedRoom = savedGroupRoom();
        given(chatRoomPort.save(any(ChatRoom.class))).willReturn(savedRoom);

        // when
        ChatGroupRoomResult result = groupRoomRegistrar.create(
                ROOM_NAME, REQUESTER_ID,
                List.of(MEMBER_USER_ID, SECOND_MEMBER_USER_ID), "방장");

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.roomType()).isEqualTo(RoomType.GROUP);
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);

        ArgumentCaptor<List<ChatRoomParticipant>> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantPort).saveAll(captor.capture());
        List<ChatRoomParticipant> participants = captor.getValue();
        assertThat(participants).hasSize(3);
        assertThat(participants).extracting(ChatRoomParticipant::getUserId)
                .containsExactly(REQUESTER_ID, MEMBER_USER_ID, SECOND_MEMBER_USER_ID);
        assertThat(participants).extracting(ChatRoomParticipant::getRole)
                .containsExactly(
                        ParticipantRole.OWNER, ParticipantRole.MEMBER, ParticipantRole.MEMBER);

        verify(chatMessageSendService).saveSystemMessage(
                eq(ROOM_ID), eq(REQUESTER_ID), eq(MessageType.SYSTEM_JOIN), anyString());
    }

    private ChatRoom savedGroupRoom() {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoom.getRoomType()).willReturn(RoomType.GROUP);
        given(chatRoom.getRoomName()).willReturn(ROOM_NAME);
        given(chatRoom.getStatus()).willReturn(RoomStatus.ACTIVE);
        return chatRoom;
    }
}
