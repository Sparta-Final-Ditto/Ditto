package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.port.ChatRoomEventPublisher;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.common.exception.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DisplayName("ChatRoomLeaveService 테스트")
class ChatRoomLeaveServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SECOND_MEMBER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String LAST_MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final String SYSTEM_LEAVE_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f71";
    private static final String SYSTEM_LEAVE_CONTENT = "사용자가 채팅방을 나갔습니다.";

    private ChatMessageSendService chatMessageSendService;
    private ChatRoomEventPublisher chatRoomEventPublisher;
    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatRoomLeaveService chatRoomLeaveService;

    @BeforeEach
    void setUp() {
        chatMessageSendService = mock(ChatMessageSendService.class);
        chatRoomEventPublisher = mock(ChatRoomEventPublisher.class);
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatRoomLeaveService = new ChatRoomLeaveService(
                chatMessageSendService,
                chatRoomEventPublisher,
                chatRoomPort,
                chatRoomParticipantPort
        );
        // 단위 테스트에서 트랜잭션 동기화 컨텍스트를 활성화해 afterCommit 콜백을 직접 실행한다.
        TransactionSynchronizationManager.initSynchronization();

        given(chatMessageSendService.saveSystemMessage(
                ROOM_ID,
                REQUESTER_ID,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT
        )).willReturn(systemLeaveMessage());
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void triggerAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    @DisplayName("1:1 채팅방을 나가면 참여자 상태를 저장하고 방을 비활성화한다")
    void leaveRoom_direct_room_success() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomType.DIRECT);
        ChatRoomParticipant requester = participant(REQUESTER_ID, ParticipantRole.MEMBER);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(requester));
        given(chatRoomParticipantPort.findActiveParticipants(ROOM_ID))
                .willReturn(List.of(requester));

        // when
        chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID);
        triggerAfterCommit();

        // then
        assertThat(requester.getLeftAt()).isNotNull();
        assertThat(requester.getLastVisibleMessageId()).isEqualTo(SYSTEM_LEAVE_MESSAGE_ID);
        verify(chatMessageSendService).saveSystemMessage(
                ROOM_ID,
                REQUESTER_ID,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT
        );
        verify(chatRoom).inactivate(REQUESTER_ID);
        then(chatRoomEventPublisher).should().notifyLeft(REQUESTER_ID, ROOM_ID);
    }

    @Test
    @DisplayName("그룹 채팅방 MEMBER가 나가면 본인 참여자 상태만 나감 처리한다")
    void leaveRoom_group_member_success() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomType.GROUP);
        ChatRoomParticipant requester = participant(REQUESTER_ID, ParticipantRole.MEMBER);
        ChatRoomParticipant owner = participant(MEMBER_USER_ID, ParticipantRole.OWNER);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(requester));
        given(chatRoomParticipantPort.findActiveParticipants(ROOM_ID))
                .willReturn(List.of(requester, owner));

        // when
        chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID);
        triggerAfterCommit();

        // then
        assertThat(requester.getLeftAt()).isNotNull();
        assertThat(requester.getLastVisibleMessageId()).isEqualTo(SYSTEM_LEAVE_MESSAGE_ID);
        verify(chatMessageSendService).saveSystemMessage(
                ROOM_ID,
                REQUESTER_ID,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT
        );
        assertThat(owner.getRole()).isEqualTo(ParticipantRole.OWNER);
        then(chatRoomEventPublisher).should().notifyLeft(REQUESTER_ID, ROOM_ID);
    }

    @Test
    @DisplayName("그룹 채팅방 OWNER가 나가면 남은 참여자에게 OWNER를 위임한다")
    void leaveRoom_group_owner_success_assign_owner() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomType.GROUP);
        ChatRoomParticipant requester = participant(REQUESTER_ID, ParticipantRole.OWNER);
        ChatRoomParticipant firstMember = mockParticipant(
                MEMBER_USER_ID,
                ParticipantRole.MEMBER,
                Instant.parse("2026-06-20T00:00:00Z")
        );
        ChatRoomParticipant secondMember = mockParticipant(
                SECOND_MEMBER_USER_ID,
                ParticipantRole.MEMBER,
                Instant.parse("2026-06-20T00:00:00Z")
        );
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(requester));
        given(chatRoomParticipantPort.findActiveParticipants(ROOM_ID))
                .willReturn(List.of(requester, firstMember, secondMember));

        // when
        chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID);
        triggerAfterCommit();

        // then
        assertThat(requester.getLeftAt()).isNotNull();
        assertThat(requester.getLastVisibleMessageId()).isEqualTo(SYSTEM_LEAVE_MESSAGE_ID);
        verify(chatMessageSendService).saveSystemMessage(
                ROOM_ID,
                REQUESTER_ID,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT
        );
        verify(firstMember).assignOwnerRole();
        then(chatRoomEventPublisher).should().notifyLeft(REQUESTER_ID, ROOM_ID);
    }

    @Test
    @DisplayName("그룹 채팅방 마지막 참여자가 나가면 방을 비활성화한다")
    void leaveRoom_group_last_participant_success_inactivate() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomType.GROUP);
        ChatRoomParticipant requester = participant(REQUESTER_ID, ParticipantRole.OWNER);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(requester));
        given(chatRoomParticipantPort.findActiveParticipants(ROOM_ID))
                .willReturn(List.of(requester));

        // when
        chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID);
        triggerAfterCommit();

        // then
        assertThat(requester.getLeftAt()).isNotNull();
        assertThat(requester.getLastVisibleMessageId()).isEqualTo(SYSTEM_LEAVE_MESSAGE_ID);
        verify(chatMessageSendService).saveSystemMessage(
                ROOM_ID,
                REQUESTER_ID,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT
        );
        verify(chatRoom).inactivate(REQUESTER_ID);
        then(chatRoomEventPublisher).should().notifyLeft(REQUESTER_ID, ROOM_ID);
    }

    @Test
    @DisplayName("채팅방이 없으면 나갈 수 없다")
    void leaveRoom_fail_room_not_found() {
        // given
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID))
                .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    @DisplayName("현재 참여자가 아니면 나갈 수 없다")
    void leaveRoom_fail_not_participant() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomType.GROUP);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomLeaveService.leaveRoom(REQUESTER_ID, ROOM_ID))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 나갈 수 없다")
    void leaveRoom_fail_null_input() {
        // when & then
        assertThatThrownBy(() -> chatRoomLeaveService.leaveRoom(null, ROOM_ID))
                .isInstanceOf(BusinessException.class);
    }

    private ChatRoom mockChatRoom(RoomType roomType) {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoom.getRoomType()).willReturn(roomType);
        given(chatRoom.getLastMessageId()).willReturn(LAST_MESSAGE_ID);
        return chatRoom;
    }

    private ChatRoomParticipant participant(UUID userId, ParticipantRole role) {
        return ChatRoomParticipant.join(ROOM_ID, userId, role);
    }

    private ChatRoomParticipant mockParticipant(
            UUID userId,
            ParticipantRole role,
            Instant joinedAt
    ) {
        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        given(participant.getUserId()).willReturn(userId);
        given(participant.getRole()).willReturn(role);
        given(participant.getJoinedAt()).willReturn(joinedAt);
        return participant;
    }

    private SentMessage systemLeaveMessage() {
        return new SentMessage(
                SYSTEM_LEAVE_MESSAGE_ID,
                ROOM_ID,
                null,
                REQUESTER_ID,
                null,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT,
                Instant.parse("2026-06-22T00:00:00Z"),
                null
        );
    }
}
