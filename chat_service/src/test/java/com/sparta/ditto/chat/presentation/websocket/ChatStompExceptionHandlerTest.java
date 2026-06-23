package com.sparta.ditto.chat.presentation.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatStompErrorResponse;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@DisplayName("ChatStompExceptionHandler 테스트")
class ChatStompExceptionHandlerTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");

    private final ChatStompExceptionHandler handler = new ChatStompExceptionHandler();

    @Test
    @DisplayName("BusinessException을 ChatStompErrorResponse로 변환한다")
    void handleBusinessException_should_return_stomp_error_response() {
        // given
        Message<?> message = stompMessage("/chat/rooms/" + ROOM_ID + "/read");
        BusinessException exception =
                new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT);

        // when
        ChatStompErrorResponse response = handler.handleBusinessException(exception, message);

        // then
        assertThat(response.code()).isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT.getCode());
        assertThat(response.errorType()).isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT.name());
        assertThat(response.message()).isEqualTo(ChatErrorCode.CHAT_NOT_PARTICIPANT.getMessage());
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.clientMessageId()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("roomId를 추출할 수 없으면 roomId는 null로 내려간다")
    void handleBusinessException_without_room_id_should_return_null_room_id() {
        // given
        Message<?> message = stompMessage("/chat/invalid");
        BusinessException exception = new BusinessException(CommonErrorCode.UNAUTHORIZED);

        // when
        ChatStompErrorResponse response = handler.handleBusinessException(exception, message);

        // then
        assertThat(response.code()).isEqualTo(CommonErrorCode.UNAUTHORIZED.getCode());
        assertThat(response.errorType()).isEqualTo(CommonErrorCode.UNAUTHORIZED.name());
        assertThat(response.roomId()).isNull();
        assertThat(response.clientMessageId()).isNull();
    }

    @Test
    @DisplayName("예상하지 못한 예외는 INTERNAL_SERVER_ERROR로 변환한다")
    void handleException_should_return_internal_server_error() {
        // given
        Message<?> message = stompMessage("/chat/rooms/" + ROOM_ID + "/presence");

        // when
        ChatStompErrorResponse response =
                handler.handleException(new RuntimeException("test"), message);

        // then
        assertThat(response.code()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.errorType()).isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.name());
        assertThat(response.message())
                .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage());
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.clientMessageId()).isNull();
    }

    private Message<?> stompMessage(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
