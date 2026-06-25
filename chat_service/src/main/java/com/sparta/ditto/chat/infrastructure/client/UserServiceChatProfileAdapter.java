package com.sparta.ditto.chat.infrastructure.client;

import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.application.room.port.ChatUserProfilePort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceChatProfileAdapter implements ChatUserProfilePort {

    private final UserServiceClient userServiceClient;
    private final CircuitBreaker userServiceChatProfileCircuitBreaker;

    @Override
    public ChatSenderProfile findProfile(UUID userId) {
        try {
            return userServiceChatProfileCircuitBreaker.executeSupplier(
                    () -> fetchProfile(userId));
        } catch (RuntimeException ex) {
            // 조회 실패·CB open(CallNotPermittedException 포함)도 발행을 막지 않도록 폴백
            log.warn("발신자 프로필 조회 실패 — 알림은 프로필 없이 발행. userId={}", userId, ex);
            return ChatSenderProfile.unknown();
        }
    }

    private ChatSenderProfile fetchProfile(UUID userId) {
        UserProfileClientResponse response = userServiceClient.getUserProfile(userId);
        if (response == null || response.data() == null) {
            return ChatSenderProfile.unknown();
        }
        return new ChatSenderProfile(
                response.data().nickname(),
                response.data().profileImageUrl());
    }
}
