package com.sparta.ditto.chat.application.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.chat.application.event.ChatMessageCreatedEvent;
import com.sparta.ditto.chat.application.event.ChatNotificationEventPublisher;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.ChatNotificationCandidateService;
import com.sparta.ditto.chat.application.room.port.ChatPresencePort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatMessageNotificationServiceTest {

    @Mock private ChatNotificationCandidateService candidateService;
    @Mock private ChatPresencePort chatPresencePort;
    @Mock private ChatRoomPort chatRoomPort;
    @Mock private ChatNotificationEventPublisher publisher;

    @InjectMocks private ChatMessageNotificationService service;

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID SENDER = UUID.randomUUID();

    private SentMessage sent(String content) {
        return new SentMessage(
                "msg-1", ROOM_ID, SENDER, null, UUID.randomUUID(),
                MessageType.TEXT, content, Instant.now(), null);
    }

    @Test
    @DisplayName("성공 - 현재 방을 보고 있지 않은 수신자에게만 발행한다")
    void dispatch_filtersActiveRoomUsers() {
        UUID inRoom = UUID.randomUUID();    // 현재 방을 보는 중 → 제외
        UUID elsewhere = UUID.randomUUID(); // 다른 방 → 대상
        UUID offline = UUID.randomUUID();   // 오프라인 → 대상

        given(candidateService.findNotificationCandidateUserIds(ROOM_ID, SENDER))
                .willReturn(List.of(inRoom, elsewhere, offline));
        given(chatPresencePort.findActiveRoomId(inRoom)).willReturn(Optional.of(ROOM_ID));
        given(chatPresencePort.findActiveRoomId(elsewhere))
                .willReturn(Optional.of(UUID.randomUUID()));
        given(chatPresencePort.findActiveRoomId(offline)).willReturn(Optional.empty());

        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        given(room.getRoomType()).willReturn(RoomType.GROUP);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room));

        service.dispatch(sent("안녕하세요"));

        ArgumentCaptor<ChatMessageCreatedEvent> captor =
                ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(publisher).publish(captor.capture());
        ChatMessageCreatedEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("CHAT_MESSAGE_CREATED");
        assertThat(event.roomType()).isEqualTo(RoomType.GROUP);
        assertThat(event.receiverIds()).containsExactlyInAnyOrder(elsewhere, offline);
    }

    @Test
    @DisplayName("발행 생략 - 모든 후보가 현재 방을 보고 있으면 발행하지 않는다")
    void dispatch_skipsWhenNoReceivers() {
        UUID inRoom = UUID.randomUUID();
        given(candidateService.findNotificationCandidateUserIds(ROOM_ID, SENDER))
                .willReturn(List.of(inRoom));
        given(chatPresencePort.findActiveRoomId(inRoom)).willReturn(Optional.of(ROOM_ID));

        service.dispatch(sent("hi"));

        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("preview - content가 50자를 넘으면 절단한다")
    void dispatch_truncatesPreview() {
        UUID offline = UUID.randomUUID();
        given(candidateService.findNotificationCandidateUserIds(ROOM_ID, SENDER))
                .willReturn(List.of(offline));
        given(chatPresencePort.findActiveRoomId(offline)).willReturn(Optional.empty());
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        given(room.getRoomType()).willReturn(RoomType.DIRECT);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room));

        service.dispatch(sent("a".repeat(80)));

        ArgumentCaptor<ChatMessageCreatedEvent> captor =
                ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().preview()).hasSize(50);
    }
}
