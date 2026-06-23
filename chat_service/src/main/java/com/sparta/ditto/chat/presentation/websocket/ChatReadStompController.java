package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.application.room.ChatReadService;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatReadRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatReadStompController {

    private final ChatReadService chatReadService;

    @MessageMapping("/chat/rooms/{roomId}/read")
    public void updateReadState(
            Principal principal,
            @DestinationVariable UUID roomId,
            @Valid @Payload ChatReadRequest request
    ) {
        UUID requesterId = StompPrincipalResolver.resolveUserId(principal);
        ChatReadCommand command = ChatReadCommand.of(
                requesterId,
                roomId,
                request.lastReadMessageId()
        );

        chatReadService.updateReadState(command);
    }
}
