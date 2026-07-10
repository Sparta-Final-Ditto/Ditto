package com.sparta.ditto.match.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * [Infrastructure Layer] 매칭 결과 캐시 서비스
 *
 * Cache-Aside 패턴
 * 1. 캐시에서 먼저 조회
 * 2. 없으면 DB 조회
 * 3. DB 결과를 캐시에 저장
 * 4. 다음 요청은 캐시에서 조회
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 오늘 자정까지만 캐싱 (하루 1회 매칭이라)
    private static final String MATCH_RESULT_PREFIX = "match:result:";
    private static final String BATCH_SCORE_PREFIX = "match:batch:score:";
    private static final String USER_TAGS_PREFIX = "match:tags:";
    private static final String USER_TIMESLOT_PREFIX = "match:timeslot:";

    /**
     * 오늘 매칭 결과 캐싱
     * TTL = 오늘 자정까지
     */
    public void cacheMatchResult(UUID userId, MatchResponseDto response) {
        try {
            String key = MATCH_RESULT_PREFIX + userId + ":" + LocalDate.now();
            String value = objectMapper.writeValueAsString(response);

            // 자정까지 TTL 계산
            long secondsUntilMidnight = getSecondsUntilMidnight();
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(secondsUntilMidnight));

            log.info("매칭 결과 캐싱 완료: userId={}", userId);
        } catch (Exception e) {
            log.error("매칭 결과 캐싱 실패: userId={}", userId, e);
        }
    }

    /**
     * 오늘 매칭 결과 조회
     */
    public MatchResponseDto getMatchResult(UUID userId) {
        try {
            String key = MATCH_RESULT_PREFIX + userId + ":" + LocalDate.now();
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return null;
            }
            return objectMapper.readValue(value, MatchResponseDto.class);
        } catch (Exception e) {
            log.error("매칭 결과 조회 실패: userId={}", userId, e);
            return null;
        }
    }

    /**
     * 새벽 배치 final_score 캐싱 (Sorted Set)
     * ZADD match:batch:score:{userId} score matchedUserId
     * → 점수 순으로 자동 정렬
     * → 매칭 버튼 클릭 시 상위 후보 빠르게 조회
     */
    public void cacheBatchScore(UUID userId, UUID matchedUserId, double score) {
        String key = BATCH_SCORE_PREFIX + userId;
        redisTemplate.opsForZSet().add(key, matchedUserId.toString(), score);
        redisTemplate.expire(key, Duration.ofHours(24));
        log.info("배치 점수 캐싱: userId={}, matchedUserId={}, score={}", userId, matchedUserId, score);
    }

    /**
     * 상위 N명 매칭 후보 조회 (Sorted Set)
     * ZREVRANGE → 높은 점수 순으로 조회
     */
    public java.util.Set<String> getTopCandidates(UUID userId, int limit) {
        String key = BATCH_SCORE_PREFIX + userId;
        return redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);
    }

    /**
     * 유저 태그 캐싱 (Kafka POST_CREATED 이벤트에서 받아온 태그)
     * Set 자료구조 → 중복 자동 제거
     */
    public void cacheUserTags(UUID userId, java.util.List<String> tags) {
        String key = USER_TAGS_PREFIX + userId;
        redisTemplate.opsForSet().add(key, tags.toArray(new String[0]));
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    /**
     * 유저 태그 조회
     */
    public java.util.Set<String> getUserTags(UUID userId) {
        String key = USER_TAGS_PREFIX + userId;
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * 유저 시간대 캐싱
     */
    public void cacheUserTimeSlot(UUID userId, String timeSlot) {
        String key = USER_TIMESLOT_PREFIX + userId;
        redisTemplate.opsForValue().set(key, timeSlot, Duration.ofDays(7));
    }

    /**
     * 유저 시간대 조회
     */
    public String getUserTimeSlot(UUID userId) {
        String key = USER_TIMESLOT_PREFIX + userId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 오늘 자정까지 남은 초 계산
     */
    private long getSecondsUntilMidnight() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.Duration.between(now, midnight).getSeconds();
    }
}
