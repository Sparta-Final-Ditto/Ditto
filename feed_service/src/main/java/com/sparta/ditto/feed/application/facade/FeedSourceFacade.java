package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetMatchFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.application.service.FeedService;
import com.sparta.ditto.feed.domain.type.Visibility;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 외부 피드 소스(매칭/팔로우) 호출을 <b>트랜잭션 밖</b>에서 수행하는 퍼사드.
 *
 * <p>매칭/팔로우 피드는 상대 서비스(match/user)를 OpenFeign으로 조회한 뒤 그 결과로 DB를 조회한다.
 * 외부 호출(그리고 {@link Retry} 재시도 대기, {@link CircuitBreaker} 판정)을 DB 트랜잭션 안에서
 * 수행하면 외부 서비스 지연 시간만큼 DB 커넥션을 점유해 커넥션 풀 고갈로 장애가 전파된다.
 * 따라서 이 퍼사드는 외부 호출과 Resilience4j를 트랜잭션 밖에서 처리하고,
 * 확정된 사용자 ID 목록만 {@link FeedService}의 {@code @Transactional} 조회 메서드에 전달한다.</p>
 *
 * <p>진입 지점({@link FeedFacade})과 이 퍼사드는 서로 다른 빈이므로 cross-bean 호출로 CB/Retry AOP가
 * 정상 적용된다. fallback은 기존과 동일하게 {@link FeedService#getRandomFeed} 랜덤 피드로 우회한다.</p>
 */
@Component
@RequiredArgsConstructor
public class FeedSourceFacade {

    private final MatchServicePort matchServicePort;
    private final FollowServicePort followServicePort;
    private final FeedService feedService;

    /**
     * 매칭 피드: match-service 추천 조회(트랜잭션 밖) → 차단 prune → PUBLIC 게시글 DB 조회.
     * 조회 실패/서킷 OPEN 시 {@link #fallbackGetMatchFeed} 랜덤 피드로 우회한다.
     */
    @CircuitBreaker(name = "matchServiceClient", fallbackMethod = "fallbackGetMatchFeed")
    @Retry(name = "matchServiceClient")
    public FeedResult getMatchFeed(GetMatchFeedQuery query, List<UUID> blockedIds) {
        RecommendationResult recommendations = matchServicePort
                .getRecommendations(query.userId(), 50);
        List<UUID> recommendedUserIds = prune(recommendations.recommendedUserIds(), blockedIds);

        return feedService.getFeedByUserIds(
                query.userId(), recommendedUserIds, List.of(Visibility.PUBLIC),
                query.cursor(), query.size());
    }

    @SuppressWarnings("unused")
    public FeedResult fallbackGetMatchFeed(
            GetMatchFeedQuery query, List<UUID> blockedIds, Throwable t) {
        return feedService.getRandomFeed(
                new GetRandomFeedQuery(query.userId(), query.cursor(), query.size()), blockedIds);
    }

    /**
     * 팔로우 피드: user-service 팔로잉 조회(트랜잭션 밖) → 차단 prune →
     * PUBLIC/FOLLOWERS_ONLY 게시글 DB 조회. 조회 실패/서킷 OPEN 시
     * {@link #fallbackGetFollowFeed} 랜덤 피드로 우회한다.
     */
    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "fallbackGetFollowFeed")
    @Retry(name = "userServiceClient")
    public FeedResult getFollowFeed(GetFollowFeedQuery query, List<UUID> blockedIds) {
        FollowingResult following = followServicePort.getFollowingIds(query.userId());
        List<UUID> followingUserIds = prune(following.followingUserIds(), blockedIds);

        return feedService.getFeedByUserIds(
                query.userId(), followingUserIds,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS_ONLY),
                query.cursor(), query.size());
    }

    @SuppressWarnings("unused")
    public FeedResult fallbackGetFollowFeed(
            GetFollowFeedQuery query, List<UUID> blockedIds, Throwable t) {
        return feedService.getRandomFeed(
                new GetRandomFeedQuery(query.userId(), query.cursor(), query.size()), blockedIds);
    }

    /** 소스 ID 목록에서 차단 ID를 in-memory 차감(prune)한다. blockedIds가 null/빈이면 원본을 그대로 반환. */
    private List<UUID> prune(List<UUID> sourceIds, List<UUID> blockedIds) {
        if (blockedIds == null || blockedIds.isEmpty()) {
            return sourceIds;
        }
        List<UUID> pruned = new ArrayList<>(sourceIds);
        pruned.removeAll(blockedIds);
        return pruned;
    }
}