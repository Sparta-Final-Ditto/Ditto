package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.presentation.dto.response.ChatMessageResponse;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageAckResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChatMessagePublisher implements ChatMessagePublisher {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/rooms/";
    private static final String ACK_DESTINATION = "/sub/chat/messages/ack";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void ackToSender(UUID senderId, SentMessage message) {
        ChatMessageAckResponse ack = ChatMessageAckResponse.of(
                message.roomId(), message.clientMessageId(),
                message.messageId(), message.createdAt());
        messagingTemplate.convertAndSendToUser(senderId.toString(), ACK_DESTINATION, ack);
    }

    @Override
    public void broadcast(UUID roomId, SentMessage message) {
        messagingTemplate.convertAndSend(
                ROOM_DESTINATION_PREFIX + roomId, ChatMessageResponse.from(message));
    }
}
