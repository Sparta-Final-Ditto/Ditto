package com.sparta.ditto.chat.presentation.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/* WebSocket / STOMP 설정
 채팅 서비스의 실시간 통신을 위한 WebSocket endpoint와 STOMP 메시지 브로커를 설정한다. */

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*"); // TODO: 운영 단계에서 허용 origin 제한
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 클라이언트가 SEND할 때 사용하는 prefix
        registry.setApplicationDestinationPrefixes("/pub");

        // 브로커가 SUBSCRIBE에 메시지를 보낼 때 사용하는 prefix
        registry.enableSimpleBroker("/sub", "/user/sub");

        // 개인 destination prefix (ACK, error 수신용)
        // /user/sub/chat/messages/ack, /user/sub/chat/errors 처럼 사용
        registry.setUserDestinationPrefix("/user");
    }
}
