package com.sparta.ditto.chat.presentation.websocket;

import com.sparta.ditto.chat.application.room.ChatPresenceService;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceCommand;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatPresenceRequest;
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
public class ChatPresenceStompController {

    private final ChatPresenceService chatPresenceService;

    @MessageMapping("/chat/rooms/{roomId}/presence")
    public void updatePresence(
            Principal principal,
            @DestinationVariable UUID roomId,
            @Valid @Payload ChatPresenceRequest request
    ) {
        UUID requesterId = StompPrincipalResolver.resolveUserId(principal);
        ChatPresenceCommand command = ChatPresenceCommand.of(
                requesterId,
                roomId,
                request.status()
        );

        chatPresenceService.updatePresence(command);
    }
}
