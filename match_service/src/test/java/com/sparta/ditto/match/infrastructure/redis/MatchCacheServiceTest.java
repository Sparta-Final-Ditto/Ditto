package com.sparta.ditto.match.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.domain.entity.MatchStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchCacheServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks
    private MatchCacheService matchCacheService;

    // ── cacheMatchResult ──────────────────────────────────────

    @Test
    @DisplayName("cacheMatchResult - 매칭 결과를 직렬화해서 Redis에 저장한다")
    void cacheMatchResult_storesInRedis() throws Exception {
        UUID userId = UUID.randomUUID();
        MatchResponseDto response = new MatchResponseDto(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f,
                Instant.now(), MatchStatus.PENDING, null);

        given(objectMapper.writeValueAsString(response)).willReturn("{\"test\":\"json\"}");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        matchCacheService.cacheMatchResult(userId, response);

        verify(valueOperations).set(anyString(), eq("{\"test\":\"json\"}"), any(Duration.class));
    }

    @Test
    @DisplayName("cacheMatchResult - 직렬화 실패 시 예외를 삼키고 정상 종료된다")
    void cacheMatchResult_serializationFails_doesNotThrow() throws Exception {
        UUID userId = UUID.randomUUID();
        MatchResponseDto response = new MatchResponseDto(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f,
                Instant.now(), MatchStatus.PENDING, null);

        given(objectMapper.writeValueAsString(response))
                .willThrow(new RuntimeException("serialize error"));

        assertThatCode(() -> matchCacheService.cacheMatchResult(userId, response))
                .doesNotThrowAnyException();
    }

    // ── getMatchResult ──────────────────────────────────────

    @Test
    @DisplayName("getMatchResult - Redis에 값이 없으면 null을 반환한다")
    void getMatchResult_nullValue_returnsNull() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        assertThat(matchCacheService.getMatchResult(userId)).isNull();
    }

    @Test
    @DisplayName("getMatchResult - Redis에 값이 있으면 역직렬화하여 반환한다")
    void getMatchResult_withValue_returnsDto() throws Exception {
        UUID userId = UUID.randomUUID();
        MatchResponseDto expected = new MatchResponseDto(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f,
                Instant.now(), MatchStatus.PENDING, null);
        String json = "{\"test\":\"json\"}";

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(json);
        given(objectMapper.readValue(json, MatchResponseDto.class)).willReturn(expected);

        assertThat(matchCacheService.getMatchResult(userId)).isEqualTo(expected);
    }

    @Test
    @DisplayName("getMatchResult - 역직렬화 실패 시 null을 반환한다")
    void getMatchResult_deserializationFails_returnsNull() throws Exception {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("bad-json");
        given(objectMapper.readValue(anyString(), eq(MatchResponseDto.class)))
                .willThrow(new RuntimeException("parse error"));

        assertThat(matchCacheService.getMatchResult(userId)).isNull();
    }

    // ── cacheBatchScore ──────────────────────────────────────

    @Test
    @DisplayName("cacheBatchScore - ZSet에 매칭 점수를 저장하고 만료 시간을 설정한다")
    void cacheBatchScore_addsToZSetAndSetsExpiry() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        double score = 0.85;

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

        matchCacheService.cacheBatchScore(userId, matchedUserId, score);

        verify(zSetOperations).add(anyString(), eq(matchedUserId.toString()), eq(score));
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    // ── getTopCandidates ──────────────────────────────────────

    @Test
    @DisplayName("getTopCandidates - 상위 후보 목록을 점수 역순으로 반환한다")
    void getTopCandidates_returnsTopN() {
        UUID userId = UUID.randomUUID();
        Set<String> expected = Set.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString());

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRange(anyString(), eq(0L), eq(4L))).willReturn(expected);

        assertThat(matchCacheService.getTopCandidates(userId, 5)).isEqualTo(expected);
    }

    // ── cacheUserTags / getUserTags ──────────────────────────────────────

    @Test
    @DisplayName("cacheUserTags - 태그 목록을 Redis Set에 저장하고 만료 시간을 설정한다")
    void cacheUserTags_addsToSetAndSetsExpiry() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForSet()).willReturn(setOperations);

        matchCacheService.cacheUserTags(userId, List.of("#tag1", "#tag2"));

        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("getUserTags - 유저의 태그 집합을 반환한다")
    void getUserTags_returnsSet() {
        UUID userId = UUID.randomUUID();
        Set<String> expected = Set.of("#tag1", "#tag2");

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.members(anyString())).willReturn(expected);

        assertThat(matchCacheService.getUserTags(userId)).isEqualTo(expected);
    }

    // ── cacheUserTimeSlot / getUserTimeSlot ──────────────────────────────────────

    @Test
    @DisplayName("cacheUserTimeSlot - 시간대를 Redis에 저장한다")
    void cacheUserTimeSlot_storesTimeSlot() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        matchCacheService.cacheUserTimeSlot(userId, "오전");

        verify(valueOperations).set(anyString(), eq("오전"), any(Duration.class));
    }

    @Test
    @DisplayName("getUserTimeSlot - 유저의 시간대를 반환한다")
    void getUserTimeSlot_returnsTimeSlot() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn("저녁");

        assertThat(matchCacheService.getUserTimeSlot(userId)).isEqualTo("저녁");
    }
}