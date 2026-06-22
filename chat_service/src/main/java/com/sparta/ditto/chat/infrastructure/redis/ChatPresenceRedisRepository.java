package com.sparta.ditto.chat.infrastructure.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatPresenceRedisRepository {

    private static final String ONLINE_KEY_PREFIX = "online:user:";
    private static final String ACTIVE_ROOM_KEY_PREFIX = "active_room:user:";
    private static final String ONLINE_VALUE = "1";
    private static final Duration ONLINE_TTL = Duration.ofSeconds(60);
    private static final Duration ACTIVE_ROOM_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;

    public void refreshOnline(UUID userId) {
        stringRedisTemplate.opsForValue()
                .set(onlineKey(userId), ONLINE_VALUE, ONLINE_TTL);
    }

    public void enterRoom(UUID userId, UUID roomId) {
        stringRedisTemplate.opsForValue()
                .set(activeRoomKey(userId), roomId.toString(), ACTIVE_ROOM_TTL);
    }

    public void leaveRoomIfCurrent(UUID userId, UUID roomId) {
        findActiveRoomId(userId)
                .filter(roomId::equals)
                .ifPresent(activeRoomId -> deleteActiveRoom(userId));
    }

    public void refreshActiveRoomTtlIfPresent(UUID userId) {
        String key = activeRoomKey(userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))) {
            stringRedisTemplate.expire(key, ACTIVE_ROOM_TTL);
        }
    }

    public Optional<UUID> findActiveRoomId(UUID userId) {
        String roomId = stringRedisTemplate.opsForValue().get(activeRoomKey(userId));
        if (roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(roomId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String onlineKey(UUID userId) {
        return ONLINE_KEY_PREFIX + userId;
    }

    private String activeRoomKey(UUID userId) {
        return ACTIVE_ROOM_KEY_PREFIX + userId;
    }

    private void deleteActiveRoom(UUID userId) {
        stringRedisTemplate.delete(activeRoomKey(userId));
    }
}
