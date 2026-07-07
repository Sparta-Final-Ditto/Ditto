package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatNotificationSettingCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatNotificationSettingResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatNotificationSettingService 테스트")
class ChatNotificationSettingServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ChatParticipantValidator chatParticipantValidator;
    private ChatRoomParticipantPort chatRoomParticipantPort;
    private ChatNotificationSettingService chatNotificationSettingService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatRoomParticipantPort = mock(ChatRoomParticipantPort.class);
        chatNotificationSettingService = new ChatNotificationSettingService(
                chatParticipantValidator,
                chatRoomParticipantPort
        );
    }

    @Test
    @DisplayName("현재 참여자의 채팅방 알림 설정을 변경한다")
    void updateNotificationSetting_success() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER
        );
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(participant));

        // when
        ChatNotificationSettingResult result =
                chatNotificationSettingService.updateNotificationSetting(command(false));

        // then
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        assertThat(participant.isNotificationEnabled()).isFalse();
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.notificationEnabled()).isFalse();
    }

    @Test
    @DisplayName("현재 참여자가 아니면 알림 설정을 변경할 수 없다")
    void updateNotificationSetting_fail_not_participant() {
        // given
        given(chatRoomParticipantPort.findActiveParticipant(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                chatNotificationSettingService.updateNotificationSetting(command(false)))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 알림 설정을 변경할 수 없다")
    void updateNotificationSetting_fail_null_command() {
        // when & then
        assertThatThrownBy(() ->
                chatNotificationSettingService.updateNotificationSetting(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private ChatNotificationSettingCommand command(boolean enabled) {
        return ChatNotificationSettingCommand.of(REQUESTER_ID, ROOM_ID, enabled);
    }
}
