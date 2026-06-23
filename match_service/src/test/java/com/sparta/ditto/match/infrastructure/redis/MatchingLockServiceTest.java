package com.sparta.ditto.match.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingLockServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MatchingLockService matchingLockService;

    @Test
    @DisplayName("락이 없으면 acquireLock은 true를 반환한다")
    void acquireLock_whenNotLocked_returnsTrue() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);

        boolean result = matchingLockService.acquireLock(userId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("락이 이미 있으면 acquireLock은 false를 반환한다")
    void acquireLock_whenAlreadyLocked_returnsFalse() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(false);

        boolean result = matchingLockService.acquireLock(userId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("setIfAbsent가 null을 반환하면 acquireLock은 false를 반환한다")
    void acquireLock_whenNullReturned_returnsFalse() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(null);

        boolean result = matchingLockService.acquireLock(userId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("releaseLock은 Redis 키를 삭제한다")
    void releaseLock_deletesLockKey() {
        UUID userId = UUID.randomUUID();

        matchingLockService.releaseLock(userId);

        verify(redisTemplate).delete("matching:lock:" + userId);
    }
}
