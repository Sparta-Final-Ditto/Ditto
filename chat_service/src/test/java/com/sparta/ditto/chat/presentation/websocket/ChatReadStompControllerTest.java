package com.sparta.ditto.chat.presentation.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.ChatReadService;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatReadRequest;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatReadStompController 테스트")
class ChatReadStompControllerTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String LAST_READ_MESSAGE_ID =
            "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";

    private ChatReadService chatReadService;
    private ChatReadStompController chatReadStompController;

    @BeforeEach
    void setUp() {
        chatReadService = mock(ChatReadService.class);
        chatReadStompController = new ChatReadStompController(chatReadService);
    }

    @Test
    @DisplayName("Principal의 userId와 요청값으로 읽음 처리 command를 만들어 서비스에 전달한다")
    void updateReadState_success() {
        // given
        Principal principal = () -> REQUESTER_ID.toString();
        ChatReadRequest request = new ChatReadRequest(LAST_READ_MESSAGE_ID);

        // when
        chatReadStompController.updateReadState(principal, ROOM_ID, request);

        // then
        ArgumentCaptor<ChatReadCommand> captor =
                ArgumentCaptor.forClass(ChatReadCommand.class);
        verify(chatReadService).updateReadState(captor.capture());

        ChatReadCommand command = captor.getValue();
        assertThat(command.requesterId()).isEqualTo(REQUESTER_ID);
        assertThat(command.roomId()).isEqualTo(ROOM_ID);
        assertThat(command.lastReadMessageId()).isEqualTo(LAST_READ_MESSAGE_ID);
    }

    @Test
    @DisplayName("Principal이 없으면 인증 오류를 던진다")
    void updateReadState_fail_null_principal() {
        // when & then
        assertThatThrownBy(() -> chatReadStompController.updateReadState(
                null,
                ROOM_ID,
                new ChatReadRequest(LAST_READ_MESSAGE_ID)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Principal name이 UUID가 아니면 인증 오류를 던진다")
    void updateReadState_fail_invalid_principal_name() {
        // given
        Principal principal = () -> "invalid-user-id";

        // when & then
        assertThatThrownBy(() -> chatReadStompController.updateReadState(
                principal,
                ROOM_ID,
                new ChatReadRequest(LAST_READ_MESSAGE_ID)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
