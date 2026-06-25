package com.sparta.ditto.chat.infrastructure.client;

import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.application.room.port.ChatUserProfilePort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceChatProfileAdapter implements ChatUserProfilePort {

    private final UserServiceClient userServiceClient;

    @Override
    public ChatSenderProfile findProfile(UUID userId) {
        try {
            UserProfileClientResponse response = userServiceClient.getUserProfile(userId);
            if (response == null || response.data() == null) {
                return ChatSenderProfile.unknown();
            }
            return new ChatSenderProfile(
                    response.data().nickname(),
                    response.data().profileImageUrl());
        } catch (RuntimeException ex) {
            // 프로필 조회 실패가 알림 발행을 막지 않도록 폴백
            log.warn("발신자 프로필 조회 실패 — 알림은 프로필 없이 발행. userId={}", userId, ex);
            return ChatSenderProfile.unknown();
        }
    }
}
