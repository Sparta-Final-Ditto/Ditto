package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.dto.command.ChatGroupRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatGroupRoomResult;
import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.application.room.port.ChatUserProfilePort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
import com.sparta.ditto.chat.domain.exception.ChatBlockedUserException;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

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

    private ChatUserValidationPort chatUserValidationPort;
    private ChatUserProfilePort chatUserProfilePort;
    private ChatGroupRoomRegistrar groupRoomRegistrar;
    private ChatGroupRoomService chatGroupRoomService;

    @BeforeEach
    void setUp() {
        chatUserValidationPort = mock(ChatUserValidationPort.class);
        chatUserProfilePort = mock(ChatUserProfilePort.class);
        groupRoomRegistrar = mock(ChatGroupRoomRegistrar.class);
        given(chatUserProfilePort.findProfile(REQUESTER_ID))
                .willReturn(new ChatSenderProfile("방장", null));
        given(groupRoomRegistrar.create(any(), any(), anyList(), anyString()))
                .willReturn(ChatGroupRoomResult.of(
                        ROOM_ID, RoomType.GROUP, ROOM_NAME, RoomStatus.ACTIVE));
        chatGroupRoomService = new ChatGroupRoomService(
                chatUserValidationPort,
                chatUserProfilePort,
                groupRoomRegistrar
        );
    }

    @Test
    @DisplayName("검증·닉네임 조회를 먼저 끝낸 뒤 registrar에 저장을 위임한다")
    void createGroupRoom_should_validate_then_delegate() {
        // when
        ChatGroupRoomResult result = chatGroupRoomService.createGroupRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);

        // 외부 호출(검증→닉네임)이 저장(registrar)보다 먼저 실행되어야 한다.
        InOrder order = Mockito.inOrder(
                chatUserValidationPort, chatUserProfilePort, groupRoomRegistrar);
        order.verify(chatUserValidationPort).validateGroupChatParticipants(
                REQUESTER_ID, List.of(MEMBER_USER_ID, SECOND_MEMBER_USER_ID));
        order.verify(chatUserProfilePort).findProfile(REQUESTER_ID);
        order.verify(groupRoomRegistrar).create(
                eq(ROOM_NAME), eq(REQUESTER_ID),
                eq(List.of(MEMBER_USER_ID, SECOND_MEMBER_USER_ID)), eq("방장"));
    }

    @Test
    @DisplayName("요청자와 중복 참여자는 MEMBER 목록에서 제외하고 위임한다")
    void createGroupRoom_should_remove_requester_and_duplicate_members() {
        // given
        ChatGroupRoomCreateCommand command = ChatGroupRoomCreateCommand.of(
                REQUESTER_ID,
                List.of(REQUESTER_ID, MEMBER_USER_ID, MEMBER_USER_ID, SECOND_MEMBER_USER_ID),
                ROOM_NAME
        );

        // when
        chatGroupRoomService.createGroupRoom(command);

        // then
        ArgumentCaptor<List<UUID>> membersCaptor = ArgumentCaptor.captor();
        verify(groupRoomRegistrar).create(
                eq(ROOM_NAME), eq(REQUESTER_ID), membersCaptor.capture(), eq("방장"));
        assertThat(membersCaptor.getValue())
                .containsExactly(MEMBER_USER_ID, SECOND_MEMBER_USER_ID);
    }

    @Test
    @DisplayName("차단 관계가 확인되면 닉네임 조회·저장을 하지 않는다")
    void createGroupRoom_should_reject_blocked_user_before_persist() {
        // given
        doThrow(new ChatBlockedUserException())
                .when(chatUserValidationPort)
                .validateGroupChatParticipants(
                        REQUESTER_ID, List.of(MEMBER_USER_ID, SECOND_MEMBER_USER_ID));

        // when & then
        assertThatThrownBy(() -> chatGroupRoomService.createGroupRoom(command()))
                .isInstanceOf(ChatBlockedUserException.class);
        verify(chatUserProfilePort, never()).findProfile(any());
        verify(groupRoomRegistrar, never()).create(any(), any(), anyList(), anyString());
    }

    @Test
    @DisplayName("요청자 포함 참여자가 3명 미만이면 그룹방을 생성할 수 없다")
    void createGroupRoom_should_reject_less_than_three_participants() {
        // given
        ChatGroupRoomCreateCommand command = ChatGroupRoomCreateCommand.of(
                REQUESTER_ID, List.of(REQUESTER_ID), ROOM_NAME);

        // when & then
        assertThatThrownBy(() -> chatGroupRoomService.createGroupRoom(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_INVALID_GROUP_PARTICIPANTS));
        verify(groupRoomRegistrar, never()).create(any(), any(), anyList(), anyString());
    }

    @Test
    @DisplayName("그룹방 이름은 비어 있을 수 없다")
    void createGroupRoomCommand_should_reject_blank_room_name() {
        assertThatThrownBy(() -> ChatGroupRoomCreateCommand.of(
                REQUESTER_ID, List.of(MEMBER_USER_ID), " "))
                .isInstanceOf(BusinessException.class);
    }

    private ChatGroupRoomCreateCommand command() {
        return ChatGroupRoomCreateCommand.of(
                REQUESTER_ID,
                List.of(MEMBER_USER_ID, SECOND_MEMBER_USER_ID),
                ROOM_NAME
        );
    }
}
