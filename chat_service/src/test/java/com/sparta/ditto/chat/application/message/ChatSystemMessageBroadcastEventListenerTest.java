package com.sparta.ditto.chat.application.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import com.sparta.ditto.chat.application.event.ChatSystemMessageBroadcastRequestedEvent;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChatSystemMessageBroadcastEventListener 테스트")
class ChatSystemMessageBroadcastEventListenerTest {

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private ChatMessagePublisher chatMessagePublisher;
    private ChatSystemMessageBroadcastEventListener listener;

    @BeforeEach
    void setUp() {
        chatMessagePublisher = mock(ChatMessagePublisher.class);
        listener = new ChatSystemMessageBroadcastEventListener(chatMessagePublisher);
    }

    @Test
    @DisplayName("이벤트를 받으면 해당 방으로 broadcast한다")
    void handle_should_broadcast() {
        // given
        SentMessage message = systemMessage("system-msg-1");
        ChatSystemMessageBroadcastRequestedEvent event =
                new ChatSystemMessageBroadcastRequestedEvent(ROOM_ID, message);

        // when
        listener.handle(event);

        // then
        verify(chatMessagePublisher).broadcast(eq(ROOM_ID), eq(message));
    }

    @Test
    @DisplayName("broadcast 실패해도 예외를 전파하지 않는다")
    void handle_should_swallow_broadcast_failure() {
        // given
        SentMessage message = systemMessage("system-msg-1");
        ChatSystemMessageBroadcastRequestedEvent event =
                new ChatSystemMessageBroadcastRequestedEvent(ROOM_ID, message);
        willThrow(new RuntimeException("stomp down"))
                .given(chatMessagePublisher).broadcast(any(), any());

        // when & then
        listener.handle(event);

        verify(chatMessagePublisher).broadcast(eq(ROOM_ID), eq(message));
    }

    private SentMessage systemMessage(String messageId) {
        return new SentMessage(
                messageId,
                ROOM_ID,
                null,
                ACTOR_ID,
                null,
                MessageType.SYSTEM_KICK,
                "사용자가 강퇴되었습니다.",
                Instant.now(),
                null
        );
    }
}
