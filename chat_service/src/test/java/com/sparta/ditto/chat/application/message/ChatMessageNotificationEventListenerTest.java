package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.event.ChatMessageNotificationRequestedEvent;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatMessageNotificationEventListener 테스트")
class ChatMessageNotificationEventListenerTest {

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ChatMessageNotificationService chatMessageNotificationService;
    private ChatMessageNotificationEventListener listener;

    @BeforeEach
    void setUp() {
        chatMessageNotificationService = mock(ChatMessageNotificationService.class);
        listener = new ChatMessageNotificationEventListener(chatMessageNotificationService);
    }

    @Test
    @DisplayName("성공 - 이벤트를 받으면 알림 dispatch를 호출한다")
    void handle_dispatchesNotification() {
        // given
        ChatMessageNotificationRequestedEvent event =
                new ChatMessageNotificationRequestedEvent(message("msg-1"));

        // when
        listener.handle(event);

        // then
        verify(chatMessageNotificationService).dispatch(event.message());
    }

    @Test
    @DisplayName("격리 - dispatch가 예외를 던져도 리스너는 예외를 전파하지 않는다(로그만)")
    void handle_swallowsDispatchException() {
        // given
        ChatMessageNotificationRequestedEvent event =
                new ChatMessageNotificationRequestedEvent(message("msg-1"));
        willThrow(new RuntimeException("dispatch failed"))
                .given(chatMessageNotificationService).dispatch(any());

        // when & then : 예외가 밖으로 전파되지 않아야 한다
        assertThatCode(() -> listener.handle(event)).doesNotThrowAnyException();
        verify(chatMessageNotificationService).dispatch(event.message());
    }

    private SentMessage message(String messageId) {
        return new SentMessage(
                messageId,
                ROOM_ID,
                SENDER_ID,
                null,
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                MessageType.TEXT,
                "hello",
                Instant.now(),
                null
        );
    }
}
