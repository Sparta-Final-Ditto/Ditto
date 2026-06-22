package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
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

        if (StompCommand.SUBSCRIBE.equals(command)) {
            UUID roomId = extractRoomId(accessor.getDestination());
            if (roomId != null) {
                // room 구독은 인증 필수: Principal(=handshake에서 확정) 없으면 막는다.
                UUID userId = resolveUserId(accessor.getUser());
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

    private UUID resolveUserId(java.security.Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            // Principal 이름이 UUID 형식이 아닌 경우도 인증 실패로 처리
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }
}
