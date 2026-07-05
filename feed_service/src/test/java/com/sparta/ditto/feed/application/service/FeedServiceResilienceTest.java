package com.sparta.ditto.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sparta.ditto.feed.application.dto.query.GetMatchFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Resilience4j CB/Retry AOP 동작 통합 테스트.
 * MatchServicePort를 @MockitoBean으로 격리하고, Spring 프록시를 통해 CB·Retry AOP가
 * 실제로 동작하는지 CircuitBreakerRegistry 상태와 호출 횟수로 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class FeedServiceResilienceTest extends PostgresTestContainerSupport {

    @MockitoBean
    private MatchServicePort matchServicePort;

    @MockitoBean
    private FollowServicePort followServicePort;

    @Autowired
    private FeedService feedService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker cb;

    @BeforeEach
    void resetCircuitBreaker() {
        cb = circuitBreakerRegistry.circuitBreaker("matchServiceClient");
        cb.reset();
    }

    // ── 005-3: 타임아웃·예외 → Fallback ────────────────────────────────────

    @Test
    @DisplayName("005-3: MatchServicePort 예외 시 예외 전파 없이 Fallback FeedResult를 반환한다")
    void tc005_3_fallback_on_exception() {
        UUID userId = UUID.randomUUID();
        given(matchServicePort.getRecommendations(any(), anyInt()))
                .willThrow(new RuntimeException("Connection timed out"));

        FeedResult result = feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());

        assertThat(result).isNotNull();
        assertThat(result.feeds()).isNotNull();
    }

    // ── 005-4: 5xx FeignException → Fallback ───────────────────────────────

    @Test
    @DisplayName("005-4: Feign 5xx 에러 시 예외 전파 없이 Fallback FeedResult를 반환한다")
    void tc005_4_fallback_on_5xx() {
        UUID userId = UUID.randomUUID();
        Request request = Request.create(
                Request.HttpMethod.GET, "/api/v1/matching/recommendations",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        FeignException internalServerError = FeignException.errorStatus(
                "getRecommendations",
                Response.builder()
                        .status(500)
                        .reason("Internal Server Error")
                        .request(request)
                        .headers(Collections.emptyMap())
                        .build());
        given(matchServicePort.getRecommendations(any(), anyInt()))
                .willThrow(internalServerError);

        FeedResult result = feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());

        assertThat(result).isNotNull();
        assertThat(result.feeds()).isNotNull();
    }

    // ── 005-10: Retry — 1회 실패 후 재시도 성공 ──────────────────────────────

    @Test
    @DisplayName("005-10: 1회 실패 후 재시도 성공 → MatchServicePort가 총 2번 호출된다")
    void tc005_10_retry_once_on_transient_failure() {
        UUID userId = UUID.randomUUID();
        given(matchServicePort.getRecommendations(any(), anyInt()))
                .willThrow(new RuntimeException("transient error"))
                .willReturn(new RecommendationResult(List.of()));

        feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());

        // maxAttempts=2 → 최초 시도(1회) + 재시도(1회) = 총 2회
        verify(matchServicePort, times(2)).getRecommendations(any(), anyInt());
    }

    // ── 005-9: CB OPEN 상태 전이 ────────────────────────────────────────────

    @Test
    @DisplayName("005-9: 실패율 임계치(minimum 3회, 50%) 도달 시 CircuitBreaker가 OPEN으로 전이된다")
    void tc005_9_circuit_opens_on_failure_threshold() {
        UUID userId = UUID.randomUUID();
        given(matchServicePort.getRecommendations(any(), anyInt()))
                .willThrow(new RuntimeException("service down"));

        // minimum-number-of-calls=3, failure-rate-threshold=50%
        // 3회 모두 실패(Retry 포함 각 2회 시도 후 CB 1회 실패 카운트) → 100% > 50% → OPEN
        for (int i = 0; i < 3; i++) {
            feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());
        }

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    // ── 005-11: OPEN → HALF_OPEN → CLOSED 복귀 ─────────────────────────────

    @Test
    @DisplayName("005-11: OPEN 1s 후 HALF_OPEN 자동 전이, 성공 2회로 CLOSED 복귀")
    void tc005_11_half_open_then_closed_after_wait() {
        UUID userId = UUID.randomUUID();
        given(matchServicePort.getRecommendations(any(), anyInt()))
                .willThrow(new RuntimeException("fail"));

        // 3회 실패 → OPEN
        for (int i = 0; i < 3; i++) {
            feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());
        }
        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // willThrow 가 활성인 상태에서 재-stubbing 시 given() 평가 자체가 throw를 발화하므로
        // willReturn().given() 패턴으로 mock을 먼저 건드리지 않고 stub 교체
        willReturn(new RecommendationResult(List.of()))
                .given(matchServicePort).getRecommendations(any(), anyInt());

        await().atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> cb.getState() == CircuitBreaker.State.HALF_OPEN);

        // permitted-number-of-calls-in-half-open-state=2 → 2회 성공 후 CLOSED
        feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());
        feedService.getMatchFeed(new GetMatchFeedQuery(userId, null, 20), List.of());

        assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}