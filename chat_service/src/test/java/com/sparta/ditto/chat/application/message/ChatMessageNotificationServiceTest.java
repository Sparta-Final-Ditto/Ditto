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
import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.application.room.port.ChatUserProfilePort;
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
    @Mock private ChatUserProfilePort chatUserProfilePort;
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
    @DisplayName("성공 - 발신자 프로필을 포함해 현재 방을 안 보는 수신자에게만 발행한다")
    void dispatch_includesSenderProfile_andFiltersActiveRoom() {
        UUID inRoom = UUID.randomUUID();    // 현재 방 → 제외
        UUID offline = UUID.randomUUID();   // 오프라인 → 대상

        given(candidateService.findNotificationCandidateUserIds(ROOM_ID, SENDER))
                .willReturn(List.of(inRoom, offline));
        given(chatPresencePort.findActiveRoomId(inRoom)).willReturn(Optional.of(ROOM_ID));
        given(chatPresencePort.findActiveRoomId(offline)).willReturn(Optional.empty());

        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        given(room.getRoomType()).willReturn(RoomType.DIRECT);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room));

        given(chatUserProfilePort.findProfile(SENDER))
                .willReturn(new ChatSenderProfile("홍길동", "https://img/p.png"));

        service.dispatch(sent("안녕하세요"));

        ArgumentCaptor<ChatMessageCreatedEvent> captor =
                ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(publisher).publish(captor.capture());
        ChatMessageCreatedEvent event = captor.getValue();
        assertThat(event.receiverIds()).containsExactly(offline);
        assertThat(event.senderNickname()).isEqualTo("홍길동");
        assertThat(event.senderProfileImageUrl()).isEqualTo("https://img/p.png");
    }

    @Test
    @DisplayName("발행 생략 - 알림 대상이 없으면 프로필 조회·발행을 하지 않는다")
    void dispatch_skipsWhenNoReceivers() {
        UUID inRoom = UUID.randomUUID();
        given(candidateService.findNotificationCandidateUserIds(ROOM_ID, SENDER))
                .willReturn(List.of(inRoom));
        given(chatPresencePort.findActiveRoomId(inRoom)).willReturn(Optional.of(ROOM_ID));

        service.dispatch(sent("hi"));

        verify(publisher, never()).publish(any());
        verify(chatUserProfilePort, never()).findProfile(any());
    }

    @Test
    @DisplayName("폴백 - 프로필이 없으면(null) 닉네임·이미지 null로 발행한다")
    void dispatch_publishesWithNullProfile() {
        UUID offline = UUID.randomUUID();
        given(candidateService.findNotificationCandidateUserIds(ROOM_ID, SENDER))
                .willReturn(List.of(offline));
        given(chatPresencePort.findActiveRoomId(offline)).willReturn(Optional.empty());
        ChatRoom room = org.mockito.Mockito.mock(ChatRoom.class);
        given(room.getRoomType()).willReturn(RoomType.GROUP);
        given(chatRoomPort.findById(ROOM_ID)).willReturn(Optional.of(room));
        given(chatUserProfilePort.findProfile(SENDER))
                .willReturn(ChatSenderProfile.unknown());

        service.dispatch(sent("hi"));

        ArgumentCaptor<ChatMessageCreatedEvent> captor =
                ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue().senderNickname()).isNull();
        assertThat(captor.getValue().senderProfileImageUrl()).isNull();
    }
}
