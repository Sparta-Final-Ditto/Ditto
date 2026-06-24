package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.presentation.dto.stomp.ChatStompErrorResponse;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import com.sparta.ditto.common.exception.ErrorCode;
import java.security.Principal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@Slf4j
@ControllerAdvice(assignableTypes = {
        ChatReadStompController.class,
        ChatPresenceStompController.class
})
public class ChatStompExceptionHandler {

    private static final String CHAT_ROOM_PATH = "/chat/rooms/";

    // 메시지 전송은 clientMessageId가 필요해 기존 로컬 핸들러를 유지하고,
    // 이 핸들러는 read/presence 실패를 공통 에러 채널로 정렬한다.
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/sub/chat/errors")
    public ChatStompErrorResponse handleBusinessException(
            BusinessException exception,
            Message<?> message
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        UUID roomId = extractRoomId(message);
        UUID userId = extractUserId(message);
        log.warn("STOMP chat business exception. userId={}, roomId={}, errorCode={}",
                userId, roomId, errorCode.getCode());
        return ChatStompErrorResponse.of(
                errorCode.getCode(),
                errorType(errorCode),
                errorCode.getMessage(),
                roomId,
                null
        );
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/sub/chat/errors")
    public ChatStompErrorResponse handleException(
            Exception exception,
            Message<?> message
    ) {
        UUID roomId = extractRoomId(message);
        UUID userId = extractUserId(message);
        log.error("Unhandled STOMP exception. userId={}, roomId={}",
                userId, roomId, exception);

        return ChatStompErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.name(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                roomId,
                null
        );
    }

    private UUID extractRoomId(Message<?> message) {
        // read/presence 실패 응답은 clientMessageId가 없으므로 destination에서 roomId만 복원한다.
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null) {
            return null;
        }

        int roomPathIndex = destination.indexOf(CHAT_ROOM_PATH);
        if (roomPathIndex < 0) {
            return null;
        }

        int roomIdStartIndex = roomPathIndex + CHAT_ROOM_PATH.length();
        int roomIdEndIndex = destination.indexOf('/', roomIdStartIndex);
        String roomId = roomIdEndIndex < 0
                ? destination.substring(roomIdStartIndex)
                : destination.substring(roomIdStartIndex, roomIdEndIndex);

        try {
            return UUID.fromString(roomId);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID extractUserId(Message<?> message) {
        Principal principal = SimpMessageHeaderAccessor.wrap(message).getUser();
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String errorType(ErrorCode errorCode) {
        return (errorCode instanceof Enum<?> enumCode)
                ? enumCode.name()
                : errorCode.getClass().getSimpleName();
    }
}
