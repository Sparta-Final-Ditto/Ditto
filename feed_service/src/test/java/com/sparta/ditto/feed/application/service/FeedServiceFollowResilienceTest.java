package com.sparta.ditto.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.support.PostgresTestContainerSupport;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * 팔로우 피드 Resilience4j CB/Retry AOP 동작 통합 테스트.
 * FollowServicePort를 @MockBean으로 격리하고, Spring 프록시를 통해 CB·Retry AOP가
 * 실제로 동작하는지 CircuitBreakerRegistry 상태와 호출 횟수로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class FeedServiceFollowResilienceTest extends PostgresTestContainerSupport {

    @MockBean
    private MatchServicePort matchServicePort;

    @MockBean
    private FollowServicePort followServicePort;

    @Autowired
    private FeedService feedService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker cb;

    @BeforeEach
    void resetCircuitBreaker() {
        cb = circuitBreakerRegistry.circuitBreaker("userServiceClient");
        cb.reset();
    }

    // ── 004-3: 타임아웃·예외 → Fallback ─────────────────────────────────────

    @Test
    @DisplayName("004-3: FollowServicePort 예외 시 예외 전파 없이 Fallback FeedResult를 반환한다")
    void tc004_3_fallback_on_exception() {
        UUID userId = UUID.randomUUID();
        given(followServicePort.getFollowingIds(any()))
                .willThrow(new RuntimeException("Connection timed out"));

        FeedResult result = feedService.getFollowFeed(new GetFollowFeedQuery(userId, null, 20));

        assertThat(result).isNotNull();
        assertThat(result.feeds()).isNotNull();
    }

    // ── 004-4: 5xx FeignException → Fallback ────────────────────────────────

    @Test
    @DisplayName("004-4: user-service 5xx 에러 시 예외 전파 없이 Fallback FeedResult를 반환한다")
    void tc004_4_fallback_on_5xx() {
        UUID userId = UUID.randomUUID();
        Request request = Request.create(
                Request.HttpMethod.GET, "/api/v1/users/" + userId + "/followings",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        FeignException internalServerError = FeignException.errorStatus(
                "getFollowings",
                Response.builder()
                        .status(500)
                        .reason("Internal Server Error")
                        .request(request)
                        .headers(Collections.emptyMap())
                        .build());
        given(followServicePort.getFollowingIds(any()))
                .willThrow(internalServerError);

        FeedResult result = feedService.getFollowFeed(new GetFollowFeedQuery(userId, null, 20));

        assertThat(result).isNotNull();
        assertThat(result.feeds()).isNotNull();
    }

    // ── 004-11: Retry — 1회 실패 후 재시도 성공 ─────────────────────────────

    @Test
    @DisplayName("004-11: 1회 실패 후 재시도 성공 → FollowServicePort가 총 2번 호출된다")
    void tc004_11_retry_once_on_transient_failure() {
        UUID userId = UUID.randomUUID();
        given(followServicePort.getFollowingIds(any()))
                .willThrow(new RuntimeException("transient error"))
                .willReturn(new FollowingResult(List.of()));

        feedService.getFollowFeed(new GetFollowFeedQuery(userId, null, 20));

        // maxAttempts=2 → 최초 시도(1회) + 재시도(1회) = 총 2회
        verify(followServicePort, times(2)).getFollowingIds(any());
    }

    // ── 004-12: CB OPEN 상태 전이 ────────────────────────────────────────────

    @Test
    @DisplayName("004-12: 실패율 임계치(minimum 3회, 50%) 도달 시 CircuitBreaker가 OPEN으로 전이된다")
    void tc004_12_circuit_opens_on_failure_threshold() {
        UUID userId = UUID.randomUUID();
        given(followServicePort.getFollowingIds(any()))
                .willThrow(new RuntimeException("service down"));

        // minimum-number-of-calls=3, failure-rate-threshold=50%
        // 3회 모두 실패(Retry 포함 각 2회 시도 후 CB 1회 실패 카운트) → 100% > 50% → OPEN
        for (int i = 0; i < 3; i++) {
            feedService.getFollowFeed(new GetFollowFeedQuery(userId, null, 20));
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }
}