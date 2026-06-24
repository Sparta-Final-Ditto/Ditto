package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.dto.command.ChatDirectRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatDirectRoomResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
import com.sparta.ditto.chat.application.room.port.DirectChatPairPort;
import com.sparta.ditto.chat.application.room.port.DirectChatPairUniqueConflictException;
import com.sparta.ditto.chat.domain.exception.ChatBlockedUserException;
import com.sparta.ditto.chat.domain.exception.ChatInvalidDirectTargetException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.DirectChatPair;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("ChatDirectRoomService 테스트")
class ChatDirectRoomServiceTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");

    private ChatRoomPort chatRoomPort;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private DirectChatPairPort directChatPairPort;
    private ChatUserValidationPort chatUserValidationPort;
    private ChatDirectRoomService chatDirectRoomService;

    @BeforeEach
    void setUp() {
        chatRoomPort = mock(ChatRoomPort.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        directChatPairPort = mock(DirectChatPairPort.class);
        chatUserValidationPort = mock(ChatUserValidationPort.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        chatDirectRoomService = new ChatDirectRoomService(
                chatRoomPort,
                chatRoomParticipantPort,
                directChatPairPort,
                chatUserValidationPort,
                transactionTemplate
        );
    }

    @Test
    @DisplayName("기존 ACTIVE 1:1 채팅방이 있으면 기존 방을 반환한다")
    void createOrGetDirectRoom_should_return_existing_active_room() {
        // given
        DirectChatPair directChatPair = DirectChatPair.create(
                ROOM_ID,
                REQUESTER_ID,
                TARGET_USER_ID
        );
        ChatRoom chatRoom = mockChatRoom(RoomStatus.ACTIVE);
        given(directChatPairPort.findByOrderedUserIds(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.of(directChatPair));
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        // when
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(result.created()).isFalse();
        assertThat(result.reactivated()).isFalse();
        verify(chatUserValidationPort).validateDirectChatTarget(REQUESTER_ID, TARGET_USER_ID);
    }

    @Test
    @DisplayName("기존 INACTIVE 1:1 채팅방이 있으면 재활성화하고 참여자 상태를 복구한다")
    void createOrGetDirectRoom_should_reactivate_existing_inactive_room() {
        // given
        DirectChatPair directChatPair = DirectChatPair.create(
                ROOM_ID,
                REQUESTER_ID,
                TARGET_USER_ID
        );
        ChatRoom chatRoom = mockChatRoom(RoomStatus.INACTIVE);
        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        given(directChatPairPort.findByOrderedUserIds(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.of(directChatPair));
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantPort.findAllParticipants(ROOM_ID))
                .willReturn(List.of(participant));

        // when
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.created()).isFalse();
        assertThat(result.reactivated()).isTrue();
        verify(chatRoom).reactivate();
        verify(participant).rejoin();
    }

    @Test
    @DisplayName("기존 1:1 채팅방이 없으면 신규 방과 참여자, pair를 생성한다")
    void createOrGetDirectRoom_should_create_new_direct_room() {
        // given
        ChatRoom savedRoom = mockChatRoom(RoomStatus.ACTIVE);
        given(directChatPairPort.findByOrderedUserIds(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.empty());
        given(chatRoomPort.saveForUniqueCheck(any(ChatRoom.class))).willReturn(savedRoom);

        // when
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(result.created()).isTrue();
        assertThat(result.reactivated()).isFalse();
        verify(chatUserValidationPort).validateDirectChatTarget(REQUESTER_ID, TARGET_USER_ID);
        verify(chatRoomParticipantPort).saveAll(any());
        verify(directChatPairPort).saveForUniqueCheck(any(DirectChatPair.class));
    }

    @Test
    @DisplayName("자기 자신과 1:1 채팅방을 만들 수 없다")
    void createOrGetDirectRoom_should_reject_self_direct_room() {
        // given
        ChatDirectRoomCreateCommand command =
                ChatDirectRoomCreateCommand.of(REQUESTER_ID, REQUESTER_ID);

        // when & then
        assertThatThrownBy(() -> chatDirectRoomService.createOrGetDirectRoom(command))
                .isInstanceOf(ChatInvalidDirectTargetException.class);
    }

    @Test
    @DisplayName("user-service 검증에서 차단 관계가 확인되면 방을 조회하거나 생성하지 않는다")
    void createOrGetDirectRoom_should_reject_blocked_user_before_room_lookup() {
        // given
        doThrow(new ChatBlockedUserException())
                .when(chatUserValidationPort)
                .validateDirectChatTarget(REQUESTER_ID, TARGET_USER_ID);

        // when & then
        assertThatThrownBy(() -> chatDirectRoomService.createOrGetDirectRoom(command()))
                .isInstanceOf(ChatBlockedUserException.class);
        verify(directChatPairPort, never()).findByOrderedUserIds(any(), any());
        verify(chatRoomPort, never()).saveForUniqueCheck(any(ChatRoom.class));
    }

    @Test
    @DisplayName("동시 생성 unique 충돌이 발생하면 기존 방을 재조회해 반환한다")
    void createOrGetDirectRoom_should_return_existing_room_after_unique_conflict() {
        // given
        DirectChatPair directChatPair = DirectChatPair.create(
                ROOM_ID,
                REQUESTER_ID,
                TARGET_USER_ID
        );
        ChatRoom savedRoom = mockChatRoom(RoomStatus.ACTIVE);
        ChatRoom existingRoom = mockChatRoom(RoomStatus.ACTIVE);
        given(directChatPairPort.findByOrderedUserIds(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.empty(), Optional.of(directChatPair));
        given(chatRoomPort.saveForUniqueCheck(any(ChatRoom.class))).willReturn(savedRoom);
        given(directChatPairPort.saveForUniqueCheck(any(DirectChatPair.class)))
                .willThrow(new DirectChatPairUniqueConflictException(
                        new RuntimeException("duplicate direct room")
                ));
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(existingRoom));

        // when
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.created()).isFalse();
        assertThat(result.reactivated()).isFalse();
    }

    private ChatDirectRoomCreateCommand command() {
        return ChatDirectRoomCreateCommand.of(REQUESTER_ID, TARGET_USER_ID);
    }

    private ChatRoom mockChatRoom(RoomStatus status) {
        ChatRoom chatRoom = Mockito.mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(ROOM_ID);
        given(chatRoom.getStatus()).willReturn(status);
        return chatRoom;
    }
}
