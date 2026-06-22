package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatNotificationCandidateService 테스트")
class ChatNotificationCandidateServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECEIVER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MUTED_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID LEFT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");

    private ChatParticipantValidator chatParticipantValidator;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private ChatNotificationCandidateService chatNotificationCandidateService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        chatNotificationCandidateService = new ChatNotificationCandidateService(
                chatParticipantValidator,
                chatRoomParticipantRepository
        );
    }

    @Test
    @DisplayName("발신자와 알림 OFF 사용자를 제외한 현재 참여자를 알림 후보로 반환한다")
    void findNotificationCandidateUserIds_success() {
        // given
        ChatRoomParticipant sender = participant(SENDER_ID);
        ChatRoomParticipant receiver = participant(RECEIVER_ID);
        ChatRoomParticipant mutedUser = participant(MUTED_USER_ID);
        mutedUser.changeNotificationEnabled(false);

        given(chatRoomParticipantRepository.findAllByRoomIdAndLeftAtIsNull(ROOM_ID))
                .willReturn(List.of(sender, receiver, mutedUser));

        // when
        List<UUID> result = chatNotificationCandidateService
                .findNotificationCandidateUserIds(ROOM_ID, SENDER_ID);

        // then
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        verify(chatParticipantValidator).ensureActiveParticipant(ROOM_ID, SENDER_ID);
        assertThat(result).containsExactly(RECEIVER_ID);
    }

    @Test
    @DisplayName("나간 사용자는 Repository 조회 조건에서 알림 후보에서 제외된다")
    void findNotificationCandidateUserIds_excludes_left_participants_by_repository_condition() {
        // given
        ChatRoomParticipant receiver = participant(RECEIVER_ID);
        ChatRoomParticipant leftUser = participant(LEFT_USER_ID);
        leftUser.leave("message-id");

        given(chatRoomParticipantRepository.findAllByRoomIdAndLeftAtIsNull(ROOM_ID))
                .willReturn(List.of(receiver));

        // when
        List<UUID> result = chatNotificationCandidateService
                .findNotificationCandidateUserIds(ROOM_ID, SENDER_ID);

        // then
        assertThat(result).containsExactly(RECEIVER_ID);
        assertThat(result).doesNotContain(LEFT_USER_ID);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 알림 후보를 조회할 수 없다")
    void findNotificationCandidateUserIds_fail_null_input() {
        // when & then
        assertThatThrownBy(() ->
                chatNotificationCandidateService.findNotificationCandidateUserIds(null, SENDER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));

        assertThatThrownBy(() ->
                chatNotificationCandidateService.findNotificationCandidateUserIds(ROOM_ID, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private ChatRoomParticipant participant(UUID userId) {
        return ChatRoomParticipant.join(ROOM_ID, userId, ParticipantRole.MEMBER);
    }
}
