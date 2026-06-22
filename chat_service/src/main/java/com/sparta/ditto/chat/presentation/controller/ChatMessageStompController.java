package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageSendRequest;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatStompErrorResponse;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import com.sparta.ditto.common.exception.ErrorCode;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatMessageStompController {

    private final ChatMessageSendService chatMessageSendService;

    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable UUID roomId,
            @Payload ChatMessageSendRequest request,
            Principal principal
    ) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        UUID senderId = UUID.fromString(principal.getName());
        ChatMessageSendCommand command = new ChatMessageSendCommand(
                roomId,
                senderId,
                request.clientMessageId(),
                request.messageType(),
                request.content()
        );
        chatMessageSendService.sendUserMessage(command);
    }

    // 권한 실패/비활성 방/검증 실패 등 도메인 예외 → 연결 종료 대신 사용자 에러 채널로
    // 실패한 메시지 매핑을 위해 roomId/clientMessageId를 함께 내려준다.
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/sub/chat/errors")
    public ChatStompErrorResponse handleBusinessException(
            BusinessException ex,
            @DestinationVariable UUID roomId,
            @Payload ChatMessageSendRequest request
    ) {
        ErrorCode errorCode = ex.getErrorCode();
        UUID clientMessageId = (request != null) ? request.clientMessageId() : null;
        return ChatStompErrorResponse.of(
                errorCode.getCode(),
                errorType(errorCode),
                errorCode.getMessage(),
                roomId,
                clientMessageId
        );
    }

    // 저장 실패 등 그 외 예외도 연결을 끊지 않고 에러 채널로 전달
    // TODO: payload 역직렬화 실패 등도 포함되어 roomId/clientMessageId는 null로 둔다.
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/sub/chat/errors")
    public ChatStompErrorResponse handleException(Exception ex) {
        return ChatStompErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                "INTERNAL_SERVER_ERROR",
                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                null,
                null
        );
    }

    private String errorType(ErrorCode errorCode) {
        return (errorCode instanceof Enum<?> enumCode)
                ? enumCode.name()
                : errorCode.getClass().getSimpleName();
    }
}
