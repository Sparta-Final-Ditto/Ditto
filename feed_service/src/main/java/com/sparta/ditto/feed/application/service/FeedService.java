package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetMatchFeedQuery;
import com.sparta.ditto.feed.application.dto.query.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedItemResult;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final MatchServicePort matchServicePort;
    private final FollowServicePort followServicePort;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Transactional(readOnly = true)
    public FeedResult getRandomFeed(GetRandomFeedQuery query, List<UUID> blockedIds) {
        return randomFeedCore(query, blockedIds);
    }

    @CircuitBreaker(name = "matchServiceClient", fallbackMethod = "fallbackGetMatchFeed")
    @Retry(name = "matchServiceClient")
    @Transactional(readOnly = true)
    public FeedResult getMatchFeed(GetMatchFeedQuery query, List<UUID> blockedIds) {
        RecommendationResult recommendations = matchServicePort
                .getRecommendations(query.userId(), 50);
        List<UUID> recommendedUserIds = prune(recommendations.recommendedUserIds(), blockedIds);

        if (recommendedUserIds.isEmpty()) {
            return new FeedResult(List.of(), null, false);
        }

        CursorContext cursor = resolveCursor(query.cursor());
        List<Post> posts = postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                recommendedUserIds, List.of(Visibility.PUBLIC),
                cursor.cursorAt(), cursor.cursorId(), query.size() + 1);

        return buildFeedResult(posts, query.userId(), query.size());
    }

    public FeedResult fallbackGetMatchFeed(
            GetMatchFeedQuery query, List<UUID> blockedIds, Throwable t) {
        return randomFeedCore(
                new GetRandomFeedQuery(query.userId(), query.cursor(), query.size()), blockedIds);
    }

    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "fallbackGetFollowFeed")
    @Retry(name = "userServiceClient")
    @Transactional(readOnly = true)
    public FeedResult getFollowFeed(GetFollowFeedQuery query, List<UUID> blockedIds) {
        FollowingResult following = followServicePort.getFollowingIds(query.userId());
        List<UUID> followingUserIds = prune(following.followingUserIds(), blockedIds);

        if (followingUserIds.isEmpty()) {
            return new FeedResult(List.of(), null, false);
        }

        CursorContext cursor = resolveCursor(query.cursor());
        List<Post> posts = postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                followingUserIds,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS_ONLY),
                cursor.cursorAt(), cursor.cursorId(), query.size() + 1);

        return buildFeedResult(posts, query.userId(), query.size());
    }

    public FeedResult fallbackGetFollowFeed(
            GetFollowFeedQuery query, List<UUID> blockedIds, Throwable t) {
        return randomFeedCore(
                new GetRandomFeedQuery(query.userId(), query.cursor(), query.size()), blockedIds);
    }

    private FeedResult randomFeedCore(GetRandomFeedQuery query, List<UUID> blockedIds) {
        CursorContext cursor = resolveCursor(query.cursorPostId());
        // 빈/null 분기는 RepositoryImpl 책임 — Service는 항상 제외 쿼리로 위임한다.
        List<Post> posts = postRepository.findFeedByVisibilityExcludingAuthorsWithCursor(
                List.of(Visibility.PUBLIC), blockedIds,
                cursor.cursorAt(), cursor.cursorId(), query.size() + 1);
        return buildFeedResult(posts, query.userId(), query.size());
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

    private CursorContext resolveCursor(UUID cursorPostId) {
        if (cursorPostId == null) {
            return new CursorContext(null, null);
        }
        Post cursorPost = postRepository.findById(cursorPostId).orElse(null);
        if (cursorPost == null) {
            return new CursorContext(null, null);
        }
        return new CursorContext(cursorPost.getCreatedAt(), cursorPost.getId());
    }

    private FeedResult buildFeedResult(List<Post> posts, UUID userId, int size) {
        boolean hasNext = posts.size() > size;
        List<Post> feedPosts = hasNext ? posts.subList(0, size) : posts;

        List<UUID> postIds = feedPosts.stream().map(Post::getId).toList();
        Set<UUID> likedPostIds = postIds.isEmpty() ? Set.of()
                : new HashSet<>(likeRepository.findPostIdsByUserIdAndPostIdIn(userId, postIds));

        List<FeedItemResult> feeds = feedPosts.stream()
                .map(p -> FeedItemResult.from(
                        p, likedPostIds.contains(p.getId()), cloudfrontDomain))
                .toList();

        UUID nextCursor = hasNext ? feedPosts.get(feedPosts.size() - 1).getId() : null;
        return new FeedResult(feeds, nextCursor, hasNext);
    }

    private record CursorContext(Instant cursorAt, UUID cursorId) {}
}
