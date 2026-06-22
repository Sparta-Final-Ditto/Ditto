package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageSendRequest;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatStompErrorResponse;
import com.sparta.ditto.chat.presentation.websocket.StompPrincipalResolver;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import com.sparta.ditto.common.exception.ErrorCode;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatMessageStompController {

    private final ChatMessageSendService chatMessageSendService;

    @MessageMapping("/chat/rooms/{roomId}/messages")
    public void sendMessage(
            @DestinationVariable UUID roomId,
            @Payload ChatMessageSendRequest request,
            Principal principal
    ) {
        if (request == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        UUID senderId = StompPrincipalResolver.resolveUserId(principal);
        ChatMessageSendCommand command = new ChatMessageSendCommand(
                roomId,
                senderId,
                request.clientMessageId(),
                request.messageType(),
                request.content()
        );
        chatMessageSendService.sendUserMessage(command);
    }

    // 메시지 전송 실패는 발신자가 요청을 매핑할 수 있도록 clientMessageId를 함께 내려준다.
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

    // 예상하지 못한 전송 실패도 연결을 끊지 않고 사용자 에러 채널로 전달한다.
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/sub/chat/errors")
    public ChatStompErrorResponse handleException(Exception ex) {
        log.error("Unhandled STOMP message exception", ex);

        return ChatStompErrorResponse.of(
                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                CommonErrorCode.INTERNAL_SERVER_ERROR.name(),
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
