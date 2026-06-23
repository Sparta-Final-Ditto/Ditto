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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingBitmapServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private MatchingBitmapService matchingBitmapService;

    @Test
    @DisplayName("비트가 설정되어 있으면 hasMatchedToday는 true를 반환한다")
    void hasMatchedToday_bitSet_returnsTrue() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getBit(anyString(), anyLong())).willReturn(true);

        boolean result = matchingBitmapService.hasMatchedToday(userId);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("비트가 설정되지 않았으면 hasMatchedToday는 false를 반환한다")
    void hasMatchedToday_bitNotSet_returnsFalse() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getBit(anyString(), anyLong())).willReturn(false);

        boolean result = matchingBitmapService.hasMatchedToday(userId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("getBit가 null을 반환하면 hasMatchedToday는 false를 반환한다")
    void hasMatchedToday_nullReturned_returnsFalse() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.getBit(anyString(), anyLong())).willReturn(null);

        boolean result = matchingBitmapService.hasMatchedToday(userId);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("markAsMatched는 비트를 설정하고 만료 시간을 설정한다")
    void markAsMatched_setsBitAndExpiry() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        matchingBitmapService.markAsMatched(userId);

        verify(valueOperations).setBit(anyString(), anyLong(), eq(true));
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }
}
