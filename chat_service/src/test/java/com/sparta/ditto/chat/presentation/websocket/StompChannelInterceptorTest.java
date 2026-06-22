package com.sparta.ditto.chat.presentation.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@DisplayName("StompChannelInterceptor 테스트")
class StompChannelInterceptorTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ChatParticipantValidator chatParticipantValidator;
    private StompChannelInterceptor stompChannelInterceptor;

    @BeforeEach
    void setUp() {
        chatParticipantValidator = mock(ChatParticipantValidator.class);
        stompChannelInterceptor = new StompChannelInterceptor(chatParticipantValidator);
    }

    @Test
    @DisplayName("room 구독 요청이면 인증된 활성 참여자인지 검증한다")
    void preSend_subscribe_room_should_validate_active_participant() {
        // given
        Message<?> message = subscribeMessage("/sub/chat/rooms/" + ROOM_ID, USER_ID);

        // when
        stompChannelInterceptor.preSend(message, mock(MessageChannel.class));

        // then
        verify(chatParticipantValidator).ensureActiveParticipant(ROOM_ID, USER_ID);
    }

    @Test
    @DisplayName("room 구독 요청의 roomId가 UUID가 아니면 INVALID_INPUT을 던진다")
    void preSend_subscribe_room_with_invalid_room_id_should_throw_invalid_input() {
        // given
        Message<?> message = subscribeMessage("/sub/chat/rooms/not-uuid", USER_ID);

        // when & then
        assertThatThrownBy(() ->
                stompChannelInterceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(CommonErrorCode.INVALID_INPUT));
    }

    @Test
    @DisplayName("room 구독 경로가 아니면 참여자 검증을 하지 않는다")
    void preSend_subscribe_other_destination_should_skip_participant_validation() {
        // given
        Message<?> message = subscribeMessage("/user/sub/chat/errors", USER_ID);

        // when
        stompChannelInterceptor.preSend(message, mock(MessageChannel.class));

        // then
        verify(chatParticipantValidator, never()).ensureActiveParticipant(ROOM_ID, USER_ID);
    }

    private Message<?> subscribeMessage(String destination, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(() -> userId.toString());
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
