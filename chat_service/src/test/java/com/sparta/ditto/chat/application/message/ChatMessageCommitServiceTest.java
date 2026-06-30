package com.sparta.ditto.chat.application.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.event.ChatMessageNotificationRequestedEvent;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.message.MessageType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ChatMessageCommitService 테스트")
class ChatMessageCommitServiceTest {

    private static final UUID ROOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    private static final UUID SENDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLIENT_MESSAGE_ID =
            UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    private ChatRoomMetadataService chatRoomMetadataService;
    private ApplicationEventPublisher applicationEventPublisher;
    private ChatMessageCommitService chatMessageCommitService;

    @BeforeEach
    void setUp() {
        chatRoomMetadataService = mock(ChatRoomMetadataService.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        chatMessageCommitService = new ChatMessageCommitService(
                chatRoomMetadataService, applicationEventPublisher);
        ReflectionTestUtils.setField(
                chatMessageCommitService, "notificationDispatchEnabled", true);
    }

    @Test
    @DisplayName("성공 - lastMessage 갱신 후 알림 요청 이벤트를 발행한다")
    void commit_updatesLastMessageAndPublishesEvent() {
        // given
        SentMessage saved = message("msg-1");

        // when
        chatMessageCommitService.commitMetadataAndRegisterNotification(ROOM_ID, saved);

        // then
        verify(chatRoomMetadataService).updateLastMessage(eq(ROOM_ID), eq("msg-1"), any());
        verify(applicationEventPublisher)
                .publishEvent(any(ChatMessageNotificationRequestedEvent.class));
    }

    @Test
    @DisplayName("성능 baseline - 알림 발행 비활성화 시 lastMessage는 갱신하되 이벤트는 발행하지 않는다")
    void commit_skipsEventWhenDisabled() {
        // given
        ReflectionTestUtils.setField(
                chatMessageCommitService, "notificationDispatchEnabled", false);
        SentMessage saved = message("msg-1");

        // when
        chatMessageCommitService.commitMetadataAndRegisterNotification(ROOM_ID, saved);

        // then
        verify(chatRoomMetadataService).updateLastMessage(eq(ROOM_ID), eq("msg-1"), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    private SentMessage message(String messageId) {
        return new SentMessage(
                messageId,
                ROOM_ID,
                SENDER_ID,
                null,
                CLIENT_MESSAGE_ID,
                MessageType.TEXT,
                "hello",
                Instant.now(),
                null
        );
    }
}
