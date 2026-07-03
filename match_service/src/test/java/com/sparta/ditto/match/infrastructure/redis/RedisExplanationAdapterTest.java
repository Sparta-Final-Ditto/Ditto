package com.sparta.ditto.match.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisExplanationAdapterTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisExplanationAdapter adapter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        adapter = new RedisExplanationAdapter(redisTemplate);
    }

    @Test
    @DisplayName("캐시에 값이 있으면 Optional로 반환한다")
    void getExplanation_found_returnsOptionalWithValue() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        String key = "explanation:" + userId + ":" + matchedUserId;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(key)).willReturn("공통 관심사: 커피");

        Optional<String> result = adapter.getExplanation(userId, matchedUserId);

        assertThat(result).contains("공통 관심사: 커피");
    }

    @Test
    @DisplayName("캐시에 값이 없으면 빈 Optional을 반환한다")
    void getExplanation_notFound_returnsEmptyOptional() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(anyString())).willReturn(null);

        Optional<String> result = adapter.getExplanation(userId, matchedUserId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Redis 조회 중 예외가 나면 빈 Optional을 반환한다")
    void getExplanation_redisThrows_returnsEmptyOptional() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willThrow(new RuntimeException("connection refused"));

        Optional<String> result = adapter.getExplanation(userId, matchedUserId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("설명을 TTL과 함께 저장한다")
    void saveExplanation_savesWithTtl() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        String key = "explanation:" + userId + ":" + matchedUserId;

        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        adapter.saveExplanation(userId, matchedUserId, "공통 관심사: 여행");

        verify(valueOperations).set(eq(key), eq("공통 관심사: 여행"), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("저장 중 예외가 나도 조용히 무시한다")
    void saveExplanation_redisThrows_doesNotPropagate() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        given(redisTemplate.opsForValue()).willThrow(new RuntimeException("connection refused"));

        // 예외가 밖으로 안 나가는지 확인 (assertThatCode의 doesNotThrowAnyException과 동일 효과)
        adapter.saveExplanation(userId, matchedUserId, "설명");
    }
}