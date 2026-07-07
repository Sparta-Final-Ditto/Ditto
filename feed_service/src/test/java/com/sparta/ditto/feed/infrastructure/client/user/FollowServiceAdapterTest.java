package com.sparta.ditto.feed.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * FollowServiceAdapter 단위 테스트 — Cache-Aside(Redis) + UserServiceClient(Feign).
 * 캐시 히트/미스, Redis GET/SET 실패 fall-through, Feign 예외 전파, 빈 목록 캐싱을 검증한다.
 * 캐시 미스 케이스가 FollowingResponse → FollowingResult 변환(data[].id 추출)도 함께 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceAdapterTest {

    private static final Duration TTL = Duration.ofMinutes(3);
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CACHE_KEY = "feed:followings:" + USER_ID;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private FollowServiceAdapter followServiceAdapter;

    @BeforeEach
    void setUp() {
        followServiceAdapter =
                new FollowServiceAdapter(userServiceClient, redisTemplate, new ObjectMapper(), TTL);
    }

    private FollowingResponse response(UUID... ids) {
        List<FollowingResponse.FollowingUser> users = java.util.Arrays.stream(ids)
                .map(FollowingResponse.FollowingUser::new)
                .toList();
        return new FollowingResponse(200, "SUCCESS", users);
    }

    @Test
    @DisplayName("캐시 히트 → Feign 미호출, 캐시 값 반환")
    void getFollowingIds_캐시히트_Feign미호출() {
        UUID cachedId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn("[\"" + cachedId + "\"]");

        FollowingResult result = followServiceAdapter.getFollowingIds(USER_ID);

        assertThat(result.followingUserIds()).containsExactly(cachedId);
        verify(userServiceClient, never()).getFollowings(any());
    }

    @Test
    @DisplayName("캐시 미스 → Feign 호출 후 TTL과 함께 캐시 저장, data[].id 추출 결과 반환")
    void getFollowingIds_캐시미스_Feign호출후_캐시저장() {
        UUID followingA = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID followingB = UUID.fromString("44444444-4444-4444-4444-444444444444");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(userServiceClient.getFollowings(USER_ID)).willReturn(response(followingA, followingB));

        FollowingResult result = followServiceAdapter.getFollowingIds(USER_ID);

        assertThat(result.followingUserIds()).containsExactly(followingA, followingB);
        verify(valueOperations)
                .set(eq(CACHE_KEY), eq("[\"" + followingA + "\",\"" + followingB + "\"]"), eq(TTL));
    }

    @Test
    @DisplayName("Redis GET 예외 → Feign 정상 호출되고 결과 반환 (fall-through)")
    void getFollowingIds_RedisGet예외_Feign정상호출() {
        UUID followingId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willThrow(new RuntimeException("Redis 다운"));
        given(userServiceClient.getFollowings(USER_ID)).willReturn(response(followingId));

        FollowingResult result = followServiceAdapter.getFollowingIds(USER_ID);

        assertThat(result.followingUserIds()).containsExactly(followingId);
        verify(userServiceClient).getFollowings(USER_ID);
    }

    @Test
    @DisplayName("Redis SET 예외 → 결과는 정상 반환")
    void getFollowingIds_RedisSet예외_결과정상반환() {
        UUID followingId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(userServiceClient.getFollowings(USER_ID)).willReturn(response(followingId));
        doThrow(new RuntimeException("Redis 다운"))
                .when(valueOperations).set(anyString(), anyString(), any());

        FollowingResult result = followServiceAdapter.getFollowingIds(USER_ID);

        assertThat(result.followingUserIds()).containsExactly(followingId);
    }

    @Test
    @DisplayName("캐시 미스 + Feign 예외 → 예외가 그대로 전파됨 (삼키지 않음)")
    void getFollowingIds_캐시미스_Feign예외_전파() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(userServiceClient.getFollowings(USER_ID))
                .willThrow(new RuntimeException("user-service 장애"));

        assertThatThrownBy(() -> followServiceAdapter.getFollowingIds(USER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("user-service 장애");
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("빈 목록(data null 포함) → \"[]\"가 캐싱됨 (Cache Penetration 방지)")
    void getFollowingIds_빈목록_빈배열캐싱() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(CACHE_KEY)).willReturn(null);
        given(userServiceClient.getFollowings(USER_ID))
                .willReturn(new FollowingResponse(200, "SUCCESS", null));

        FollowingResult result = followServiceAdapter.getFollowingIds(USER_ID);

        assertThat(result.followingUserIds()).isEmpty();
        verify(valueOperations).set(eq(CACHE_KEY), eq("[]"), eq(TTL));
    }
}