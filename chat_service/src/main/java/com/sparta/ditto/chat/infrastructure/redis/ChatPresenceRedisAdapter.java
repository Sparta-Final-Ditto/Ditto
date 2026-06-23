package com.sparta.ditto.chat.infrastructure.redis;

import com.sparta.ditto.chat.application.room.port.ChatPresencePort;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatPresenceRedisAdapter implements ChatPresencePort {

    private final ChatPresenceRedisRepository chatPresenceRedisRepository;

    @Override
    public void refreshOnline(UUID userId) {
        chatPresenceRedisRepository.refreshOnline(userId);
    }

    @Override
    public void enterRoom(UUID userId, UUID roomId) {
        chatPresenceRedisRepository.enterRoom(userId, roomId);
    }

    @Override
    public void leaveRoomIfCurrent(UUID userId, UUID roomId) {
        chatPresenceRedisRepository.leaveRoomIfCurrent(userId, roomId);
    }

    @Override
    public void refreshActiveRoomTtlIfPresent(UUID userId) {
        chatPresenceRedisRepository.refreshActiveRoomTtlIfPresent(userId);
    }

    @Override
    public Optional<UUID> findActiveRoomId(UUID userId) {
        return chatPresenceRedisRepository.findActiveRoomId(userId);
    }
}
