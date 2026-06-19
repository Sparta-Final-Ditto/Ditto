package com.sparta.ditto.chat.presentation.websocket;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

@DisplayName("WebSocketConfig 테스트")
class WebSocketConfigTest {

    private final WebSocketConfig config = new WebSocketConfig();

    @Test
    @DisplayName("STOMP 엔드포인트(/ws-chat)를 등록한다")
    void register_stomp_endpoints() {
        // given
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        given(registry.addEndpoint("/ws-chat")).willReturn(registration);

        // when
        config.registerStompEndpoints(registry);

        // then
        verify(registry).addEndpoint("/ws-chat");
        verify(registration).setAllowedOriginPatterns("*");
    }

    @Test
    @DisplayName("메시지 브로커 prefix를 설정한다")
    void configure_message_broker() {
        // given
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        // when
        config.configureMessageBroker(registry);

        // then
        verify(registry).setApplicationDestinationPrefixes("/pub");
        verify(registry).enableSimpleBroker("/sub", "/user/sub");
        verify(registry).setUserDestinationPrefix("/user");
    }
}