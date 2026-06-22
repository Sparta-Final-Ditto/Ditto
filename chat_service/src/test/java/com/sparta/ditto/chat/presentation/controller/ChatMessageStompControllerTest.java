package com.sparta.ditto.chat.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageSendRequest;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatMessageStompController 테스트")
class ChatMessageStompControllerTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_MESSAGE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000200");

    private ChatMessageSendService chatMessageSendService;
    private ChatMessageStompController chatMessageStompController;

    @BeforeEach
    void setUp() {
        chatMessageSendService = mock(ChatMessageSendService.class);
        chatMessageStompController = new ChatMessageStompController(chatMessageSendService);
    }

    @Test
    @DisplayName("Principal의 userId로 메시지 전송 command를 만들어 서비스에 전달한다")
    void sendMessage_success() {
        // given
        Principal principal = () -> SENDER_ID.toString();
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                CLIENT_MESSAGE_ID,
                MessageType.TEXT,
                "hello"
        );

        // when
        chatMessageStompController.sendMessage(ROOM_ID, request, principal);

        // then
        ArgumentCaptor<ChatMessageSendCommand> captor =
                ArgumentCaptor.forClass(ChatMessageSendCommand.class);
        verify(chatMessageSendService).sendUserMessage(captor.capture());

        ChatMessageSendCommand command = captor.getValue();
        assertThat(command.roomId()).isEqualTo(ROOM_ID);
        assertThat(command.senderId()).isEqualTo(SENDER_ID);
        assertThat(command.clientMessageId()).isEqualTo(CLIENT_MESSAGE_ID);
        assertThat(command.messageType()).isEqualTo(MessageType.TEXT);
        assertThat(command.content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Principal이 없으면 인증 오류를 던진다")
    void sendMessage_fail_null_principal() {
        // given
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                CLIENT_MESSAGE_ID,
                MessageType.TEXT,
                "hello"
        );

        // when & then
        assertThatThrownBy(() -> chatMessageStompController.sendMessage(
                ROOM_ID,
                request,
                null
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Principal name이 UUID가 아니면 인증 오류를 던진다")
    void sendMessage_fail_invalid_principal_name() {
        // given
        Principal principal = () -> "invalid-user-id";
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                CLIENT_MESSAGE_ID,
                MessageType.TEXT,
                "hello"
        );

        // when & then
        assertThatThrownBy(() -> chatMessageStompController.sendMessage(
                ROOM_ID,
                request,
                principal
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
