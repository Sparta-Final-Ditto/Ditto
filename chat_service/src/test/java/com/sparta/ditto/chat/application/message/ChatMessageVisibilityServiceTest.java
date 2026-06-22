package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.message.dto.result.ChatMessageVisibilityRange;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatMessageVisibilityService 테스트")
class ChatMessageVisibilityServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Instant JOINED_AT = Instant.parse("2026-06-22T00:00:00Z");
    private static final Instant LEFT_AT = Instant.parse("2026-06-22T01:00:00Z");
    private static final String LAST_VISIBLE_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";

    private ChatParticipantValidator chatParticipantValidator;
    private ChatMessageVisibilityService chatMessageVisibilityService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatMessageVisibilityService = new ChatMessageVisibilityService(chatParticipantValidator);
    }

    @Test
    @DisplayName("현재 참여자는 joinedAt 이후 메시지를 조회할 수 있고 상한 메시지는 없다")
    void getVisibilityRange_current_participant() {
        // given
        ChatRoomParticipant participant = participant(JOINED_AT, null, null);
        given(chatParticipantValidator.getParticipant(ROOM_ID, USER_ID)).willReturn(participant);

        // when
        ChatMessageVisibilityRange result =
                chatMessageVisibilityService.getVisibilityRange(ROOM_ID, USER_ID);

        // then
        verify(chatParticipantValidator).getParticipant(ROOM_ID, USER_ID);
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(result.lastVisibleMessageId()).isNull();
        assertThat(result.left()).isFalse();
        assertThat(result.hasUpperBound()).isFalse();
    }

    @Test
    @DisplayName("나간 참여자는 joinedAt 이후부터 lastVisibleMessageId 이하까지만 조회할 수 있다")
    void getVisibilityRange_left_participant() {
        // given
        ChatRoomParticipant participant =
                participant(JOINED_AT, LEFT_AT, LAST_VISIBLE_MESSAGE_ID);
        given(chatParticipantValidator.getParticipant(ROOM_ID, USER_ID)).willReturn(participant);

        // when
        ChatMessageVisibilityRange result =
                chatMessageVisibilityService.getVisibilityRange(ROOM_ID, USER_ID);

        // then
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.joinedAt()).isEqualTo(JOINED_AT);
        assertThat(result.lastVisibleMessageId()).isEqualTo(LAST_VISIBLE_MESSAGE_ID);
        assertThat(result.left()).isTrue();
        assertThat(result.hasUpperBound()).isTrue();
    }

    @Test
    @DisplayName("필수 입력값이 없으면 조회 가능 범위를 만들 수 없다")
    void getVisibilityRange_fail_null_input() {
        assertThatThrownBy(() -> chatMessageVisibilityService.getVisibilityRange(null, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));

        assertThatThrownBy(() -> chatMessageVisibilityService.getVisibilityRange(ROOM_ID, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("joinedAt이 없으면 메시지 조회 하한을 만들 수 없다")
    void getVisibilityRange_fail_missing_joinedAt() {
        // given
        ChatRoomParticipant participant = participant(null, null, null);
        given(chatParticipantValidator.getParticipant(ROOM_ID, USER_ID)).willReturn(participant);

        // when & then
        assertThatThrownBy(() -> chatMessageVisibilityService.getVisibilityRange(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("나간 참여자의 lastVisibleMessageId가 없으면 조회 상한을 만들 수 없다")
    void getVisibilityRange_fail_left_without_lastVisibleMessageId() {
        // given
        ChatRoomParticipant participant = participant(JOINED_AT, LEFT_AT, null);
        given(chatParticipantValidator.getParticipant(ROOM_ID, USER_ID)).willReturn(participant);

        // when & then
        assertThatThrownBy(() -> chatMessageVisibilityService.getVisibilityRange(ROOM_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private ChatRoomParticipant participant(
            Instant joinedAt,
            Instant leftAt,
            String lastVisibleMessageId
    ) {
        ChatRoomParticipant participant = mock(ChatRoomParticipant.class);
        given(participant.getJoinedAt()).willReturn(joinedAt);
        given(participant.getLeftAt()).willReturn(leftAt);
        given(participant.getLastVisibleMessageId()).willReturn(lastVisibleMessageId);
        return participant;
    }
}
