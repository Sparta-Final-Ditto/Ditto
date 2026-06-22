package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceCommand;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceStatus;
import com.sparta.ditto.chat.application.room.dto.result.ChatPresenceResult;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.infrastructure.redis.ChatPresenceRedisRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

@DisplayName("ChatPresenceService 테스트")
class ChatPresenceServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ChatParticipantValidator chatParticipantValidator;
    private ChatPresenceRedisRepository chatPresenceRedisRepository;
    private ChatPresenceService chatPresenceService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatPresenceRedisRepository = mock(ChatPresenceRedisRepository.class);
        chatPresenceService = new ChatPresenceService(
                chatParticipantValidator,
                chatPresenceRedisRepository
        );
    }

    @Test
    @DisplayName("ENTER는 현재 참여자와 ACTIVE 방을 검증한 뒤 online과 active room을 저장한다")
    void updatePresence_enter_success() {
        // when
        ChatPresenceResult result =
                chatPresenceService.updatePresence(command(ChatPresenceStatus.ENTER));

        // then
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        verify(chatParticipantValidator).ensureActiveParticipant(ROOM_ID, REQUESTER_ID);
        verify(chatPresenceRedisRepository).refreshOnline(REQUESTER_ID);
        verify(chatPresenceRedisRepository).enterRoom(REQUESTER_ID, ROOM_ID);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo(ChatPresenceStatus.ENTER);
    }

    @Test
    @DisplayName("LEAVE는 검증 없이 active room을 삭제한다")
    void updatePresence_leave_success() {
        // when
        ChatPresenceResult result =
                chatPresenceService.updatePresence(command(ChatPresenceStatus.LEAVE));

        // then
        verify(chatPresenceRedisRepository).leaveRoomIfCurrent(REQUESTER_ID, ROOM_ID);
        verifyNoInteractions(chatParticipantValidator);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.status()).isEqualTo(ChatPresenceStatus.LEAVE);
    }

    @Test
    @DisplayName("heartbeat는 online과 기존 active room TTL을 갱신한다")
    void refreshHeartbeat_success() {
        // when
        chatPresenceService.refreshHeartbeat(REQUESTER_ID);

        // then
        verify(chatPresenceRedisRepository).refreshOnline(REQUESTER_ID);
        verify(chatPresenceRedisRepository).refreshActiveRoomTtlIfPresent(REQUESTER_ID);
    }

    @Test
    @DisplayName("Redis 갱신에 실패하면 presence 요청을 서버 오류로 처리한다")
    void updatePresence_fail_redis_error() {
        // given
        doThrow(new DataAccessResourceFailureException("redis unavailable"))
                .when(chatPresenceRedisRepository)
                .enterRoom(REQUESTER_ID, ROOM_ID);

        // when & then
        assertThatThrownBy(() -> chatPresenceService.updatePresence(
                command(ChatPresenceStatus.ENTER)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode())
                        .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR));
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        verify(chatParticipantValidator).ensureActiveParticipant(ROOM_ID, REQUESTER_ID);
        verify(chatPresenceRedisRepository).refreshOnline(REQUESTER_ID);
    }

    @Test
    @DisplayName("ENTER 때 비활성 방이면 Redis 상태를 저장하지 않는다")
    void updatePresence_enter_fail_inactive_room() {
        // given
        doThrow(new BusinessException(ChatErrorCode.CHAT_ROOM_INACTIVE))
                .when(chatParticipantValidator)
                .ensureRoomActive(ROOM_ID);

        // when & then
        assertThatThrownBy(() -> chatPresenceService.updatePresence(
                command(ChatPresenceStatus.ENTER)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_ROOM_INACTIVE));
        verifyNoInteractions(chatPresenceRedisRepository);
    }

    @Test
    @DisplayName("ENTER 때 현재 참여자가 아니면 active room을 저장하지 않는다")
    void updatePresence_enter_fail_not_participant() {
        // given
        doThrow(new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT))
                .when(chatParticipantValidator)
                .ensureActiveParticipant(ROOM_ID, REQUESTER_ID);

        // when & then
        assertThatThrownBy(() -> chatPresenceService.updatePresence(
                command(ChatPresenceStatus.ENTER)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT));
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        verifyNoMoreInteractions(chatPresenceRedisRepository);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 presence 상태를 갱신하지 않는다")
    void updatePresence_fail_null_command() {
        // when & then
        assertThatThrownBy(() -> chatPresenceService.updatePresence(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
        verifyNoInteractions(chatParticipantValidator, chatPresenceRedisRepository);
    }

    private ChatPresenceCommand command(ChatPresenceStatus status) {
        return ChatPresenceCommand.of(REQUESTER_ID, ROOM_ID, status);
    }
}
