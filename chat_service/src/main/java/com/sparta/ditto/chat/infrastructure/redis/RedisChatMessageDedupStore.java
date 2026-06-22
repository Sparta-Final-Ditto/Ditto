package com.sparta.ditto.chat.infrastructure.redis;

import com.sparta.ditto.chat.application.message.port.ChatMessageDedupStore;
import com.sparta.ditto.chat.application.message.port.DedupBeginResult;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisChatMessageDedupStore implements ChatMessageDedupStore {

    private static final String KEY_PREFIX = "chat:dedup:";
    private static final String PROCESSING = "PROCESSING";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public DedupBeginResult begin(UUID roomId, UUID senderId, UUID clientMessageId) {
        String key = key(roomId, senderId, clientMessageId);
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, PROCESSING, TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return DedupBeginResult.newRequest();
        }

        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null || PROCESSING.equals(value)) {
            return DedupBeginResult.duplicateProcessing();
        }
        return DedupBeginResult.duplicateCompleted(value);
    }

    @Override
    public void complete(UUID roomId, UUID senderId, UUID clientMessageId, String messageId) {
        stringRedisTemplate.opsForValue()
                .set(key(roomId, senderId, clientMessageId), messageId, TTL);
    }

    private String key(UUID roomId, UUID senderId, UUID clientMessageId) {
        return KEY_PREFIX + roomId + ":" + senderId + ":" + clientMessageId;
    }
}
