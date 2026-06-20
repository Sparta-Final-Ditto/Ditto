package com.sparta.ditto.feed.infrastructure.client;

import com.sparta.ditto.feed.infrastructure.client.KakaoLocalClient;
import com.sparta.ditto.feed.infrastructure.client.NeighborhoodAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NeighborhoodAdapterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private KakaoLocalClient kakaoLocalClient;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private NeighborhoodAdapter neighborhoodAdapter;

    @Test
    @DisplayName("Redis 캐시 히트 → Kakao API 호출 없이 캐시 값 반환")
    void resolveNeighborhood_캐시히트_캐시값반환() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("geo:37.556:127.037")).thenReturn("서울 성동구");

        String result = neighborhoodAdapter.resolveNeighborhood(37.5563, 127.0374);

        assertThat(result).isEqualTo("서울 성동구");
        verify(kakaoLocalClient, never()).reverseGeocode(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Redis 캐시 미스 → Kakao API 호출 후 캐시 저장 및 반환")
    void resolveNeighborhood_캐시미스_API호출후_캐시저장() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(kakaoLocalClient.reverseGeocode(37.5563, 127.0374)).thenReturn("서울 성동구");

        String result = neighborhoodAdapter.resolveNeighborhood(37.5563, 127.0374);

        assertThat(result).isEqualTo("서울 성동구");
        verify(valueOperations).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Kakao API 예외 발생 → null 반환")
    void resolveNeighborhood_API예외_null반환() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(kakaoLocalClient.reverseGeocode(anyDouble(), anyDouble())).thenThrow(new RuntimeException("API 오류"));

        String result = neighborhoodAdapter.resolveNeighborhood(37.5563, 127.0374);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Kakao API null 반환 → 캐시 저장 없이 null 반환")
    void resolveNeighborhood_API_null반환_캐시저장안함() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(kakaoLocalClient.reverseGeocode(anyDouble(), anyDouble())).thenReturn(null);

        String result = neighborhoodAdapter.resolveNeighborhood(37.5563, 127.0374);

        assertThat(result).isNull();
        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }
}
