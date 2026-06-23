// infrastructure/redis/MatchingStatsService.java
package com.sparta.ditto.match.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * [Infrastructure Layer] HyperLogLog로 매칭 통계
 *
 * 왜 HyperLogLog?
 * - 중복 없이 유니크 유저 수 집계
 * - 메모리 12KB로 수억 개 데이터 처리
 * - 오차율 0.81%로 통계용으로 충분
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingStatsService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String HLL_PREFIX = "match:hll:";

    /**
     * 오늘 매칭 요청한 유저 추가
     */
    public void addMatchingUser(UUID userId) {
        String key = HLL_PREFIX + LocalDate.now();
        redisTemplate.opsForHyperLogLog().add(key, userId.toString());
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    /**
     * 오늘 매칭 요청한 유니크 유저 수 조회
     */
    public long getTodayMatchingCount() {
        String key = HLL_PREFIX + LocalDate.now();
        return redisTemplate.opsForHyperLogLog().size(key);
    }
}