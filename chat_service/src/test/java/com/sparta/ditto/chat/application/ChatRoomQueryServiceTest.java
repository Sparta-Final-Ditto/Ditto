package com.sparta.ditto.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.ChatRoomQueryService;
import com.sparta.ditto.chat.application.room.dto.ChatRoomDetailResult;
import com.sparta.ditto.chat.application.room.dto.ChatRoomSummaryResult;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatRoomQueryService 테스트")
class ChatRoomQueryServiceTest {

    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID OLD_ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000101");

    private ChatRoomRepository chatRoomRepository;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private ChatRoomQueryService chatRoomQueryService;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        chatRoomQueryService = new ChatRoomQueryService(
                chatRoomRepository,
                chatRoomParticipantRepository
        );
    }

    @Test
    @DisplayName("성공 - 채팅방 상세를 조회한다")
    void getRoom_success() {
        // given
        ChatRoom chatRoom = mockChatRoom(ROOM_ID, RoomType.GROUP, "스터디방");
        ChatRoomParticipant requester = mockParticipant(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.OWNER,
                true
        );
        ChatRoomParticipant member = mockParticipant(
                ROOM_ID,
                OTHER_USER_ID,
                ParticipantRole.MEMBER,
                true
        );
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(requester));
        given(chatRoomParticipantRepository.findAllByRoomIdAndLeftAtIsNull(ROOM_ID))
                .willReturn(List.of(requester, member));

        // when
        ChatRoomDetailResult result = chatRoomQueryService.getRoom(REQUESTER_ID, ROOM_ID);

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.roomType()).isEqualTo(RoomType.GROUP);
        assertThat(result.roomName()).isEqualTo("스터디방");
        assertThat(result.status()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(result.notificationEnabled()).isTrue();
        assertThat(result.participants()).hasSize(2);
    }

    @Test
    @DisplayName("실패 - 채팅방이 없으면 예외를 던진다")
    void getRoom_fail_room_not_found() {
        // given
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomQueryService.getRoom(REQUESTER_ID, ROOM_ID))
                .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    @DisplayName("실패 - 현재 참여자가 아니면 예외를 던진다")
    void getRoom_fail_not_participant() {
        // given
        ChatRoom chatRoom = mockChatRoom(ROOM_ID, RoomType.DIRECT, null);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomQueryService.getRoom(REQUESTER_ID, ROOM_ID))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("실패 - 나간 참여자는 채팅방 상세를 조회할 수 없다")
    void getRoom_fail_left_participant() {
        // given
        ChatRoom chatRoom = mockChatRoom(ROOM_ID, RoomType.DIRECT, null);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomQueryService.getRoom(REQUESTER_ID, ROOM_ID))
                .isInstanceOf(ChatNotParticipantException.class);
        verify(chatRoomParticipantRepository)
                .findByRoomIdAndUserIdAndLeftAtIsNull(ROOM_ID, REQUESTER_ID);
    }

    @Test
    @DisplayName("성공 - 내 채팅방 목록을 조회한다")
    void getMyRooms_success() {
        // given
        ChatRoomParticipant recentParticipant = mockParticipant(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER,
                false
        );
        ChatRoomParticipant oldParticipant = mockParticipant(
                OLD_ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER,
                true
        );
        ChatRoom recentRoom = mockChatRoom(ROOM_ID, RoomType.GROUP, "최근방");
        ChatRoom oldRoom = mockChatRoom(OLD_ROOM_ID, RoomType.DIRECT, null);
        given(recentRoom.getLastMessageAt())
                .willReturn(Instant.parse("2026-06-20T01:00:00Z"));
        given(oldRoom.getLastMessageAt())
                .willReturn(Instant.parse("2026-06-19T01:00:00Z"));
        given(chatRoomParticipantRepository.findAllByUserIdAndLeftAtIsNullAndHiddenFalse(
                REQUESTER_ID
        )).willReturn(List.of(recentParticipant, oldParticipant));
        given(chatRoomRepository.findAllByIdsOrderByLastMessageAtDesc(
                List.of(ROOM_ID, OLD_ROOM_ID)
        )).willReturn(List.of(recentRoom, oldRoom));

        // when
        List<ChatRoomSummaryResult> results = chatRoomQueryService.getMyRooms(REQUESTER_ID);

        // then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).roomId()).isEqualTo(ROOM_ID);
        assertThat(results.get(0).lastMessage()).isNull();
        assertThat(results.get(0).unreadCount()).isZero();
        assertThat(results.get(0).notificationEnabled()).isFalse();
        assertThat(results.get(1).roomId()).isEqualTo(OLD_ROOM_ID);
        assertThat(results.get(1).notificationEnabled()).isTrue();
        verify(chatRoomParticipantRepository)
                .findAllByUserIdAndLeftAtIsNullAndHiddenFalse(REQUESTER_ID);
        verify(chatRoomRepository)
                .findAllByIdsOrderByLastMessageAtDesc(List.of(ROOM_ID, OLD_ROOM_ID));
    }

    @Test
    @DisplayName("성공 - 참여 중인 채팅방이 없으면 빈 목록을 반환한다")
    void getMyRooms_success_empty() {
        // given
        given(chatRoomParticipantRepository.findAllByUserIdAndLeftAtIsNullAndHiddenFalse(
                REQUESTER_ID
        )).willReturn(List.of());

        // when
        List<ChatRoomSummaryResult> results = chatRoomQueryService.getMyRooms(REQUESTER_ID);

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("성공 - 나간 방과 숨김 방은 기본 목록 조회 대상에서 제외한다")
    void getMyRooms_should_use_active_and_visible_participants_only() {
        // given
        given(chatRoomParticipantRepository.findAllByUserIdAndLeftAtIsNullAndHiddenFalse(
                REQUESTER_ID
        )).willReturn(List.of());

        // when
        List<ChatRoomSummaryResult> results = chatRoomQueryService.getMyRooms(REQUESTER_ID);

        // then
        assertThat(results).isEmpty();
        verify(chatRoomParticipantRepository)
                .findAllByUserIdAndLeftAtIsNullAndHiddenFalse(REQUESTER_ID);
    }

    @Test
    @DisplayName("성공 - 방 목록은 Repository 정렬 결과 순서를 유지한다")
    void getMyRooms_should_keep_repository_sorted_order() {
        // given
        ChatRoomParticipant recentParticipant = mockParticipant(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER,
                true
        );
        ChatRoomParticipant oldParticipant = mockParticipant(
                OLD_ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER,
                true
        );
        ChatRoom recentRoom = mockChatRoom(ROOM_ID, RoomType.GROUP, "최근방");
        ChatRoom oldRoom = mockChatRoom(OLD_ROOM_ID, RoomType.DIRECT, null);
        given(chatRoomParticipantRepository.findAllByUserIdAndLeftAtIsNullAndHiddenFalse(
                REQUESTER_ID
        )).willReturn(List.of(oldParticipant, recentParticipant));
        given(chatRoomRepository.findAllByIdsOrderByLastMessageAtDesc(
                List.of(OLD_ROOM_ID, ROOM_ID)
        )).willReturn(List.of(recentRoom, oldRoom));

        // when
        List<ChatRoomSummaryResult> results = chatRoomQueryService.getMyRooms(REQUESTER_ID);

        // then
        assertThat(results)
                .extracting(ChatRoomSummaryResult::roomId)
                .containsExactly(ROOM_ID, OLD_ROOM_ID);
    }

    private ChatRoom mockChatRoom(UUID roomId, RoomType roomType, String roomName) {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getId()).willReturn(roomId);
        given(chatRoom.getRoomType()).willReturn(roomType);
        given(chatRoom.getRoomName()).willReturn(roomName);
        given(chatRoom.getStatus()).willReturn(RoomStatus.ACTIVE);
        return chatRoom;
    }

    private ChatRoomParticipant mockParticipant(
            UUID roomId,
            UUID userId,
            ParticipantRole role,
            boolean notificationEnabled
    ) {
        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        given(participant.getRoomId()).willReturn(roomId);
        given(participant.getUserId()).willReturn(userId);
        given(participant.getRole()).willReturn(role);
        given(participant.getJoinedAt()).willReturn(Instant.parse("2026-06-20T00:00:00Z"));
        given(participant.getLeftAt()).willReturn(null);
        given(participant.isNotificationEnabled()).willReturn(notificationEnabled);
        return participant;
    }
}
