package com.sparta.ditto.chat.application.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatParticipantValidator 테스트")
class ChatParticipantValidatorImplTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ChatRoomRepository chatRoomRepository;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private ChatParticipantValidator validator;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        validator = new ChatParticipantValidatorImpl(
                chatRoomRepository,
                chatRoomParticipantRepository
        );
    }

    @Test
    @DisplayName("현재 참여자이면 검증에 성공한다")
    void ensureActiveParticipant_success() {
        // given
        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                USER_ID
        )).willReturn(Optional.of(participant));

        // when & then
        assertThatCode(() -> validator.ensureActiveParticipant(ROOM_ID, USER_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("현재 참여자가 아니면 실패한다")
    void ensureActiveParticipant_fail_not_participant() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                USER_ID
        )).willReturn(Optional.empty());
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.ensureActiveParticipant(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    @Test
    @DisplayName("채팅방이 없으면 현재 참여자 검증에 실패한다")
    void ensureActiveParticipant_fail_room_not_found() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                USER_ID
        )).willReturn(Optional.empty());
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> validator.ensureActiveParticipant(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("나간 참여자는 현재 참여자로 볼 수 없다")
    void ensureActiveParticipant_fail_left_participant() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                USER_ID
        )).willReturn(Optional.empty());
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.ensureActiveParticipant(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    @Test
    @DisplayName("채팅방이 ACTIVE이면 검증에 성공한다")
    void ensureRoomActive_success() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomStatus.ACTIVE);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        // when & then
        assertThatCode(() -> validator.ensureRoomActive(ROOM_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("채팅방이 없으면 실패한다")
    void ensureRoomActive_fail_room_not_found() {
        // given
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> validator.ensureRoomActive(ROOM_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    @Test
    @DisplayName("채팅방이 INACTIVE이면 실패한다")
    void ensureRoomActive_fail_room_inactive() {
        // given
        ChatRoom chatRoom = mockChatRoom(RoomStatus.INACTIVE);
        given(chatRoomRepository.findById(ROOM_ID)).willReturn(Optional.of(chatRoom));

        // when & then
        assertThatThrownBy(() -> validator.ensureRoomActive(ROOM_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_ROOM_INACTIVE));
    }

    @Test
    @DisplayName("참여자 메타데이터를 조회한다")
    void getParticipant_success() {
        // given
        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        given(chatRoomParticipantRepository.findByRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.of(participant));

        // when
        ChatRoomParticipant result = validator.getParticipant(ROOM_ID, USER_ID);

        // then
        assertThat(result).isSameAs(participant);
    }

    @Test
    @DisplayName("참여자 메타데이터가 없으면 실패한다")
    void getParticipant_fail_not_participant() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> validator.getParticipant(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    @Test
    @DisplayName("채팅방이 없으면 참여자 메타데이터 조회에 실패한다")
    void getParticipant_fail_room_not_found() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserId(ROOM_ID, USER_ID))
                .willReturn(Optional.empty());
        given(chatRoomRepository.existsById(ROOM_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> validator.getParticipant(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_ROOM_NOT_FOUND));
    }

    private ChatRoom mockChatRoom(RoomStatus status) {
        ChatRoom chatRoom = mock(ChatRoom.class);
        given(chatRoom.getStatus()).willReturn(status);
        return chatRoom;
    }
}
