package com.sparta.ditto.chat.presentation.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // STOMP 채널 처리 스레드풀/큐 설정.
    // 기본값은 CPU 코어 기반이라 로컬 Docker에서 작게 잡혀 동시 부하 시 메시지 처리가 밀린다.
    // 측정 근거(2026-06-30, ack-only saturation): inbound/outbound를 16/64 → 32/128로 올리니
    //   VU200 ACK p95가 약 4.1s → 1.8s대로 개선됐다(약 2배). 고부하 병목은 DB 커넥션 풀 슬롯이
    //   아니라(풀 20→40은 무효) 메시지 처리 스레드 수였다. 그래서 32/128로 확정.
    // outbound 큐가 더 큰 이유: 메시지 1건이 방 인원수만큼 broadcast되어 나가는 양이 더 많다.
    // TODO: 배포 시 application.yml로 분리하고 AWS 등 실제 환경에서 재측정해 환경별로 튜닝한다.
    private static final int INBOUND_CORE_POOL_SIZE = 32;
    private static final int INBOUND_MAX_POOL_SIZE = 128;
    private static final int INBOUND_QUEUE_CAPACITY = 1000;
    private static final int OUTBOUND_CORE_POOL_SIZE = 32;
    private static final int OUTBOUND_MAX_POOL_SIZE = 128;
    private static final int OUTBOUND_QUEUE_CAPACITY = 2000;

    private final StompChannelInterceptor stompChannelInterceptor;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final ChatHandshakeHandler chatHandshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setHandshakeHandler(chatHandshakeHandler)
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns("*"); // TODO: 운영 단계에서 허용 origin 제한
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
