package com.sparta.ditto.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.dto.ChatDirectRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.ChatDirectRoomResult;
import com.sparta.ditto.chat.application.room.ChatDirectRoomService;
import com.sparta.ditto.chat.domain.exception.ChatInvalidDirectTargetException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.DirectChatPair;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.chat.infrastructure.jpa.DirectChatPairRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
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

    private ChatRoomRepository chatRoomRepository;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private DirectChatPairRepository directChatPairRepository;
    private ChatDirectRoomService chatDirectRoomService;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        directChatPairRepository = mock(DirectChatPairRepository.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        chatDirectRoomService = new ChatDirectRoomService(
                chatRoomRepository,
                chatRoomParticipantRepository,
                directChatPairRepository,
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
        given(directChatPairRepository.findByUser1IdAndUser2Id(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.of(directChatPair));
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        // when
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(result.created()).isFalse();
        assertThat(result.reactivated()).isFalse();
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
        given(directChatPairRepository.findByUser1IdAndUser2Id(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.of(directChatPair));
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantRepository.findAllByRoomId(ROOM_ID))
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
        given(directChatPairRepository.findByUser1IdAndUser2Id(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.empty());
        given(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).willReturn(savedRoom);

        // when
        ChatDirectRoomResult result = chatDirectRoomService.createOrGetDirectRoom(command());

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(result.created()).isTrue();
        assertThat(result.reactivated()).isFalse();
        verify(chatRoomParticipantRepository).saveAll(any());
        verify(directChatPairRepository).saveAndFlush(any(DirectChatPair.class));
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
        given(directChatPairRepository.findByUser1IdAndUser2Id(REQUESTER_ID, TARGET_USER_ID))
                .willReturn(Optional.empty(), Optional.of(directChatPair));
        given(chatRoomRepository.saveAndFlush(any(ChatRoom.class))).willReturn(savedRoom);
        given(directChatPairRepository.saveAndFlush(any(DirectChatPair.class)))
                .willThrow(new DataIntegrityViolationException("duplicate direct room"));
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(existingRoom));

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
