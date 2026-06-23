package com.sparta.ditto.match.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * [Infrastructure Layer] 매칭 분산 락 서비스
 *
 * 동시성 문제 해결!
 * 문제 상황:
 * - 유저가 매칭 버튼 동시에 2번 클릭
 * - 두 요청이 동시에 "오늘 매칭 없음" 확인
 * - 둘 다 매칭 생성 → 하루 1회 제한 뚫림!
 *
 * 해결:
 * - Redis에 락 걸어서 동시 요청 차단
 * - 첫 번째 요청만 처리, 두 번째는 에러
 *
 * 관계:
 * - RedisTemplate 주입받아 사용
 * - MatchService에서 매칭 시작/완료 시 호출
 */
@Component
@RequiredArgsConstructor
public class MatchingLockService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "matching:lock:"; // Redis 키 prefix
    private static final Duration LOCK_TTL = Duration.ofSeconds(10); // 10초 후 자동 해제

    /**
     * 락 획득
     * setIfAbsent = "없으면 설정" → 이미 있으면 false 반환
     * TTL 설정으로 서버 장애 시에도 자동 해제
     */
    public boolean acquireLock(UUID userId) {
        String lockKey = LOCK_PREFIX + userId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 락 해제
     * 매칭 완료 후 반드시 해제해야 함
     * MatchService의 finally 블록에서 호출
     */
    public void releaseLock(UUID userId) {
        String lockKey = LOCK_PREFIX + userId;
        redisTemplate.delete(lockKey);
    }
}