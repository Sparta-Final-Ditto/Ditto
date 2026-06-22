package com.sparta.ditto.chat.presentation.controller;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageSendRequest;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
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
}
