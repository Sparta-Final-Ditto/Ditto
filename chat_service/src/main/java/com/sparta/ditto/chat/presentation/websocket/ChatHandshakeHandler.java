package com.sparta.ditto.chat.presentation.websocket;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class ChatHandshakeHandler extends DefaultHandshakeHandler {

    // handshake에서 저장한 userId로 세션 Principal을 확정한다.
    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        Object userId = attributes.get(ChatHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return (userId != null) ? new StompPrincipal(userId.toString()) : null;
    }
}
