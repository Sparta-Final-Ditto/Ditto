package com.sparta.ditto.match.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HyperLogLogOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingStatsServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private HyperLogLogOperations<String, String> hyperLogLogOperations;

    @InjectMocks
    private MatchingStatsService matchingStatsService;

    @Test
    @DisplayName("addMatchingUser는 HyperLogLog에 유저를 추가하고 만료 시간을 설정한다")
    void addMatchingUser_addsToHyperLogLogAndSetsExpiry() {
        UUID userId = UUID.randomUUID();
        given(redisTemplate.opsForHyperLogLog()).willReturn(hyperLogLogOperations);

        matchingStatsService.addMatchingUser(userId);

        verify(hyperLogLogOperations).add(anyString(), eq(userId.toString()));
        verify(redisTemplate).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("getTodayMatchingCount는 오늘 매칭한 유니크 유저 수를 반환한다")
    void getTodayMatchingCount_returnsCount() {
        given(redisTemplate.opsForHyperLogLog()).willReturn(hyperLogLogOperations);
        given(hyperLogLogOperations.size(anyString())).willReturn(42L);

        long count = matchingStatsService.getTodayMatchingCount();

        assertThat(count).isEqualTo(42L);
    }
}
