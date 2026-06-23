package com.sparta.ditto.feed.infrastructure.client;

import com.sparta.ditto.feed.application.UploadUrlResult.port.out.NeighborhoodPort;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/** NeighborhoodPort 구현체 — Redis 캐시 후 Kakao API 호출 */
public class NeighborhoodAdapter implements NeighborhoodPort {

    private static final String CACHE_KEY_PREFIX = "geo:";
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final KakaoLocalClient kakaoLocalClient;

    @Override
    public String resolveNeighborhood(double latitude, double longitude) {
        String cacheKey = buildCacheKey(latitude, longitude);

        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return cached;
            }

            String neighborhood = kakaoLocalClient.reverseGeocode(latitude, longitude);
            if (neighborhood != null) {
                redisTemplate.opsForValue().set(cacheKey, neighborhood, TTL);
            }
            return neighborhood;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildCacheKey(double latitude, double longitude) {
        return String.format(CACHE_KEY_PREFIX + "%.3f:%.3f", latitude, longitude);
    }
}
