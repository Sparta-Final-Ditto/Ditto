package com.sparta.ditto.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.dto.ChatGroupRoomCreateCommand;
import com.sparta.ditto.chat.application.dto.ChatGroupRoomResult;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatGroupRoomService 테스트")
class ChatGroupRoomServiceTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final String ROOM_NAME = "스터디 그룹";

    private ChatRoomRepository chatRoomRepository;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private ChatGroupRoomService chatGroupRoomService;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        chatGroupRoomService = new ChatGroupRoomService(
                chatRoomRepository,
                chatRoomParticipantRepository
        );
    }

    @Test
    @DisplayName("그룹 채팅방 생성 시 방과 OWNER/MEMBER 참여자를 저장한다")
    void createGroupRoom_should_create_room_and_participants() {
        // given
        ChatRoom savedRoom = savedGroupRoom();
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(savedRoom);

        // when
        ChatGroupRoomResult result = chatGroupRoomService.createGroupRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.roomType()).isEqualTo(RoomType.GROUP);
        assertThat(result.roomName()).isEqualTo(ROOM_NAME);
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);

        ArgumentCaptor<List<ChatRoomParticipant>> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantRepository).saveAll(captor.capture());
        List<ChatRoomParticipant> participants = captor.getValue();

        assertThat(participants).hasSize(3);
        assertThat(participants)
                .extracting(ChatRoomParticipant::getUserId)
                .containsExactly(REQUESTER_ID, MEMBER_USER_ID, SECOND_MEMBER_USER_ID);
        assertThat(participants)
                .extracting(ChatRoomParticipant::getRole)
                .containsExactly(
                        ParticipantRole.OWNER,
                        ParticipantRole.MEMBER,
                        ParticipantRole.MEMBER
                );
    }

    @Test
    @DisplayName("요청자와 중복 참여자는 MEMBER 목록에서 제외한다")
    void createGroupRoom_should_remove_requester_and_duplicate_members() {
        // given
        ChatRoom savedRoom = savedGroupRoom();
        given(chatRoomRepository.save(any(ChatRoom.class))).willReturn(savedRoom);
        ChatGroupRoomCreateCommand command = ChatGroupRoomCreateCommand.of(
                REQUESTER_ID,
                List.of(REQUESTER_ID, MEMBER_USER_ID, MEMBER_USER_ID, SECOND_MEMBER_USER_ID),
                ROOM_NAME
        );

        // when
        chatGroupRoomService.createGroupRoom(command);

        // then
        ArgumentCaptor<List<ChatRoomParticipant>> captor = ArgumentCaptor.captor();
        verify(chatRoomParticipantRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .extracting(ChatRoomParticipant::getUserId)
                .containsExactly(REQUESTER_ID, MEMBER_USER_ID, SECOND_MEMBER_USER_ID);
    }

    @Test
    @DisplayName("요청자 포함 참여자가 3명 미만이면 그룹방을 생성할 수 없다")
    void createGroupRoom_should_reject_less_than_three_participants() {
        // given
        ChatGroupRoomCreateCommand command = ChatGroupRoomCreateCommand.of(
                REQUESTER_ID,
                List.of(REQUESTER_ID),
                ROOM_NAME
        );

        // when & then
        assertThatThrownBy(() -> chatGroupRoomService.createGroupRoom(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_INVALID_GROUP_PARTICIPANTS));
    }

    @Test
    @DisplayName("그룹방 이름은 비어 있을 수 없다")
    void createGroupRoomCommand_should_reject_blank_room_name() {
        // when & then
        assertThatThrownBy(() -> ChatGroupRoomCreateCommand.of(
                REQUESTER_ID,
                List.of(MEMBER_USER_ID),
                " "
        ))
                .isInstanceOf(BusinessException.class);
    }

    private ChatGroupRoomCreateCommand command() {
        return ChatGroupRoomCreateCommand.of(
                REQUESTER_ID,
                List.of(MEMBER_USER_ID, SECOND_MEMBER_USER_ID),
                ROOM_NAME
        );
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
