package com.sparta.ditto.feed.application.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetMatchFeedQuery;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.support.PostgresTestContainerSupport;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 경계 회귀 방지 테스트.
 *
 * <p>매칭/팔로우 피드의 외부 서비스 호출({@link MatchServicePort}/{@link FollowServicePort})이
 * DB 트랜잭션 <b>밖</b>에서 수행되는지 검증한다. 포트 호출 시점에
 * {@link TransactionSynchronizationManager#isActualTransactionActive()}가 {@code false}여야 한다.
 * 외부 호출이 다시 {@code @Transactional} 안으로 들어가면(=회귀) 이 테스트가 실패한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class FeedSourceFacadeTransactionBoundaryTest extends PostgresTestContainerSupport {

    @MockitoBean
    private MatchServicePort matchServicePort;

    @MockitoBean
    private FollowServicePort followServicePort;

    @Autowired
    private FeedSourceFacade feedSourceFacade;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.circuitBreaker("matchServiceClient").reset();
        circuitBreakerRegistry.circuitBreaker("userServiceClient").reset();
    }

    @Test
    @DisplayName("매칭 피드: MatchServicePort 호출 시점에 활성 트랜잭션이 없어야 한다")
    void matchServiceCall_runsOutsideTransaction() {
        AtomicBoolean txActiveDuringCall = new AtomicBoolean(true);
        given(matchServicePort.getRecommendations(any(), anyInt()))
                .willAnswer(invocation -> {
                    txActiveDuringCall.set(
                            TransactionSynchronizationManager.isActualTransactionActive());
                    return new RecommendationResult(List.of());
                });

        feedSourceFacade.getMatchFeed(
                new GetMatchFeedQuery(UUID.randomUUID(), null, 20), List.of());

        assertThat(txActiveDuringCall).isFalse();
    }

    @Test
    @DisplayName("팔로우 피드: FollowServicePort 호출 시점에 활성 트랜잭션이 없어야 한다")
    void followServiceCall_runsOutsideTransaction() {
        AtomicBoolean txActiveDuringCall = new AtomicBoolean(true);
        given(followServicePort.getFollowingIds(any()))
                .willAnswer(invocation -> {
                    txActiveDuringCall.set(
                            TransactionSynchronizationManager.isActualTransactionActive());
                    return new FollowingResult(List.of());
                });

        feedSourceFacade.getFollowFeed(
                new GetFollowFeedQuery(UUID.randomUUID(), null, 20), List.of());

        assertThat(txActiveDuringCall).isFalse();
    }
}