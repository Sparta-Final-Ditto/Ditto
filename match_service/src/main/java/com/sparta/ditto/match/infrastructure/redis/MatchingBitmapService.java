package com.sparta.ditto.match.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * [Infrastructure Layer] Bitmap으로 하루 1회 제한 체크
 *
 * 왜 Bitmap?
 * - DB 조회보다 훨씬 빠름
 * - 메모리 효율적
 * - 유저 100만명도 100만 비트 = 125KB만 사용
 *
 * 구조:
 * match:bitmap:20260620 → 비트맵
 * userId의 해시값 위치 비트가 1이면 오늘 매칭 완료
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingBitmapService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BITMAP_PREFIX = "match:bitmap:";

    /**
     * 오늘 매칭 여부 체크
     */
    public boolean hasMatchedToday(UUID userId) {
        String key = BITMAP_PREFIX + LocalDate.now();
        long offset = Math.abs(userId.hashCode());
        Boolean matched = redisTemplate.opsForValue().getBit(key, offset);
        return Boolean.TRUE.equals(matched);
    }

    /**
     * 오늘 매칭 완료 표시
     */
    public void markAsMatched(UUID userId) {
        String key = BITMAP_PREFIX + LocalDate.now();
        long offset = Math.abs(userId.hashCode());
        redisTemplate.opsForValue().setBit(key, offset, true);
        redisTemplate.expire(key, Duration.ofDays(1));
        log.info("매칭 완료 표시: userId={}", userId);
    }
}