package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.application.room.port.ChatRoomEventPublisher;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChatRoomEventPublisher implements ChatRoomEventPublisher {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/rooms/";

    private final SimpMessagingTemplate messagingTemplate;

    // convertAndSendToUser의 첫 번째 인자는 STOMP Principal name과 일치해야 한다.
    // ChatHandshakeHandler가 X-User-Id를 userId.toString()으로 Principal을 세팅하므로 일치한다.
    @Override
    public void notifyLeft(UUID userId, UUID roomId) {
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                ROOM_DESTINATION_PREFIX + roomId + "/left",
                Map.of("roomId", roomId.toString()));
    }
}
