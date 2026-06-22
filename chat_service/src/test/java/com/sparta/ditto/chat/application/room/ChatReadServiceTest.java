package com.sparta.ditto.chat.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatReadService 테스트")
class ChatReadServiceTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String LAST_READ_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";

    private ChatParticipantValidator chatParticipantValidator;
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    private ChatReadService chatReadService;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        chatRoomParticipantRepository = mock(ChatRoomParticipantRepository.class);
        chatReadService = new ChatReadService(
                chatParticipantValidator,
                chatRoomParticipantRepository
        );
    }

    @Test
    @DisplayName("현재 참여자의 마지막 읽은 메시지와 읽은 시각을 갱신한다")
    void updateReadState_success() {
        // given
        ChatRoomParticipant participant = ChatRoomParticipant.join(
                ROOM_ID,
                REQUESTER_ID,
                ParticipantRole.MEMBER
        );
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.of(participant));

        // when
        ChatReadResult result = chatReadService.updateReadState(command());

        // then
        verify(chatParticipantValidator).ensureRoomActive(ROOM_ID);
        assertThat(participant.getLastReadMessageId()).isEqualTo(LAST_READ_MESSAGE_ID);
        assertThat(participant.getLastReadAt()).isNotNull();
        assertThat(result.roomId()).isEqualTo(ROOM_ID);
        assertThat(result.lastReadMessageId()).isEqualTo(LAST_READ_MESSAGE_ID);
        assertThat(result.lastReadAt()).isEqualTo(participant.getLastReadAt());
    }

    @Test
    @DisplayName("현재 참여자가 아니면 읽음 상태를 갱신할 수 없다")
    void updateReadState_fail_not_participant() {
        // given
        given(chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                ROOM_ID,
                REQUESTER_ID
        )).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(command()))
                .isInstanceOf(ChatNotParticipantException.class);
    }

    @Test
    @DisplayName("필수 입력값이 없으면 읽음 상태를 갱신할 수 없다")
    void updateReadState_fail_null_command() {
        // when & then
        assertThatThrownBy(() -> chatReadService.updateReadState(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    private ChatReadCommand command() {
        return ChatReadCommand.of(REQUESTER_ID, ROOM_ID, LAST_READ_MESSAGE_ID);
    }
}
