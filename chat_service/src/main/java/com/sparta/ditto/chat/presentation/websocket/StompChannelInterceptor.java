package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final String ROOM_SUBSCRIBE_PREFIX = "/sub/chat/rooms/";

    private final ChatParticipantValidator chatParticipantValidator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            // TODO: JWT 검증으로 교체. 현재는 X-User-Id 헤더로 임시 사용자 식별.
            String userId = accessor.getFirstNativeHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                accessor.setUser(() -> userId);
            }
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            UUID roomId = extractRoomId(accessor.getDestination());
            if (roomId != null) {
                // room 구독은 인증된 활성 참여자만 허용한다.
                UUID userId = StompPrincipalResolver.resolveUserId(accessor.getUser());
                chatParticipantValidator.ensureActiveParticipant(roomId, userId);
            }
        }
        return message;
    }

    private UUID extractRoomId(String destination) {
        if (destination == null || !destination.startsWith(ROOM_SUBSCRIBE_PREFIX)) {
            return null;
        }
        String roomId = destination.substring(ROOM_SUBSCRIBE_PREFIX.length());
        try {
            return UUID.fromString(roomId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
