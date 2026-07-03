package com.sparta.ditto.chat.presentation.websocket;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // STOMP 채널 처리 스레드풀. 기본값(CPU 코어 기반)은 동시 부하 시 처리가 밀려 명시 지정.
    // 고부하 병목이 처리 스레드 수여서 부하 측정 결과 상향했다.
    // outbound 큐가 더 큰 건 1건이 방 인원수만큼 broadcast되기 때문.
    // TODO: 배포 시 application.yml로 분리해 환경별 튜닝.
    private static final int INBOUND_CORE_POOL_SIZE = 32;
    private static final int INBOUND_MAX_POOL_SIZE = 128;
    private static final int INBOUND_QUEUE_CAPACITY = 1000;
    private static final int OUTBOUND_CORE_POOL_SIZE = 32;
    private static final int OUTBOUND_MAX_POOL_SIZE = 128;
    private static final int OUTBOUND_QUEUE_CAPACITY = 2000;

    private final StompChannelInterceptor stompChannelInterceptor;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final ChatHandshakeHandler chatHandshakeHandler;
    // 환경별 허용 origin 목록(콤마 구분). 로컬은 기본값, 배포는 CHAT_ALLOWED_ORIGINS로 주입.
    private final List<String> allowedOrigins;

    public WebSocketConfig(
            StompChannelInterceptor stompChannelInterceptor,
            ChatHandshakeInterceptor chatHandshakeInterceptor,
            ChatHandshakeHandler chatHandshakeHandler,
            @Value("${chat.websocket.allowed-origins}") List<String> allowedOrigins) {
        this.stompChannelInterceptor = stompChannelInterceptor;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
        this.chatHandshakeHandler = chatHandshakeHandler;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setHandshakeHandler(chatHandshakeHandler)
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/pub");
        registry.enableSimpleBroker("/sub");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(INBOUND_CORE_POOL_SIZE)
                .maxPoolSize(INBOUND_MAX_POOL_SIZE)
                .queueCapacity(INBOUND_QUEUE_CAPACITY);
        registration.interceptors(stompChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(OUTBOUND_CORE_POOL_SIZE)
                .maxPoolSize(OUTBOUND_MAX_POOL_SIZE)
                .queueCapacity(OUTBOUND_QUEUE_CAPACITY);
    }
}
