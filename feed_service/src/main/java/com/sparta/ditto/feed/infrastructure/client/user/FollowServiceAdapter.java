package com.sparta.ditto.feed.infrastructure.client.user;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FollowServiceAdapter implements FollowServicePort {

    private static final String CACHE_KEY_PREFIX = "feed:followings:";
    private static final TypeReference<List<UUID>> ID_LIST_TYPE = new TypeReference<>() {};

    private final UserServiceClient userServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public FollowServiceAdapter(
            UserServiceClient userServiceClient,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.cache.followings-ttl:3m}") Duration ttl) {
        this.userServiceClient = userServiceClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public FollowingResult getFollowingIds(UUID userId) {
        String cacheKey = CACHE_KEY_PREFIX + userId;

        List<UUID> cached = readCache(cacheKey);
        if (cached != null) {
            return new FollowingResult(cached);
        }

        // Feign 호출은 try-catch로 감싸지 않는다. FeignException은 그대로 전파되어야 한다.
        FollowingResponse response = userServiceClient.getFollowings(userId);
        List<UUID> followingIds = response.data() == null
                ? List.of()
                : response.data().stream()
                        .map(FollowingResponse.FollowingUser::id)
                        .toList();

        writeCache(cacheKey, followingIds);
        return new FollowingResult(followingIds);
    }

    /** Redis GET 실패·역직렬화 실패는 캐시 미스로 취급(fall-through). */
    private List<UUID> readCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, ID_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패, Feign 호출로 진행합니다. key={}", cacheKey, e);
            return null;
        }
    }

    /** Redis SET 실패는 삼키고 Feign 결과를 정상 반환한다. */
    private void writeCache(String cacheKey, List<UUID> followingIds) {
        try {
            String value = objectMapper.writeValueAsString(followingIds);
            redisTemplate.opsForValue().set(cacheKey, value, ttl);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패, 결과는 정상 반환합니다. key={}", cacheKey, e);
        }
    }
}
