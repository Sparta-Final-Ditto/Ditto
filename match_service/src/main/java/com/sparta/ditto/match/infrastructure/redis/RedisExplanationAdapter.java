package com.sparta.ditto.match.infrastructure.redis;

import com.sparta.ditto.match.application.port.ExplanationCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisExplanationAdapter implements ExplanationCachePort {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String KEY_PREFIX = "explanation:";
    private static final Duration TTL = Duration.ofHours(24);

    @Override
    public Optional<String> getExplanation(UUID userId, UUID matchedUserId) {
        try {
            String key = KEY_PREFIX + userId + ":" + matchedUserId;
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            log.warn("[Redis] explanation 조회 실패 userId={}", userId);
            return Optional.empty();
        }
    }

    @Override
    public void saveExplanation(UUID userId, UUID matchedUserId, String explanation) {
        try {
            String key = KEY_PREFIX + userId + ":" + matchedUserId;
            redisTemplate.opsForValue().set(key, explanation, TTL);
        } catch (Exception e) {
            log.warn("[Redis] explanation 저장 실패 userId={}", userId);
        }
    }
}