package com.sparta.ditto.chat.presentation.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.room.ChatPresenceService;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceCommand;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceStatus;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatPresenceRequest;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("ChatPresenceStompController 테스트")
class ChatPresenceStompControllerTest {

    private static final UUID ROOM_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID REQUESTER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ChatPresenceService chatPresenceService;
    private ChatPresenceStompController chatPresenceStompController;

    @BeforeEach
    void setUp() {
        chatPresenceService = mock(ChatPresenceService.class);
        chatPresenceStompController = new ChatPresenceStompController(chatPresenceService);
    }

    @Test
    @DisplayName("Principal을 userId로 사용해 presence command를 만들고 서비스에 전달한다")
    void updatePresence_success() {
        // given
        Principal principal = () -> REQUESTER_ID.toString();
        ChatPresenceRequest request = new ChatPresenceRequest(ChatPresenceStatus.ENTER);

        // when
        chatPresenceStompController.updatePresence(principal, ROOM_ID, request);

        // then
        ArgumentCaptor<ChatPresenceCommand> captor =
                ArgumentCaptor.forClass(ChatPresenceCommand.class);
        verify(chatPresenceService).updatePresence(captor.capture());

        ChatPresenceCommand command = captor.getValue();
        assertThat(command.requesterId()).isEqualTo(REQUESTER_ID);
        assertThat(command.roomId()).isEqualTo(ROOM_ID);
        assertThat(command.status()).isEqualTo(ChatPresenceStatus.ENTER);
    }

    @Test
    @DisplayName("Principal이 없으면 인증 오류를 던진다")
    void updatePresence_fail_null_principal() {
        // when & then
        assertThatThrownBy(() -> chatPresenceStompController.updatePresence(
                null,
                ROOM_ID,
                new ChatPresenceRequest(ChatPresenceStatus.ENTER)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Principal name이 UUID가 아니면 인증 오류를 던진다")
    void updatePresence_fail_invalid_principal_name() {
        // given
        Principal principal = () -> "invalid-user-id";

        // when & then
        assertThatThrownBy(() -> chatPresenceStompController.updatePresence(
                principal,
                ROOM_ID,
                new ChatPresenceRequest(ChatPresenceStatus.ENTER)
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHORIZED));
    }
}
