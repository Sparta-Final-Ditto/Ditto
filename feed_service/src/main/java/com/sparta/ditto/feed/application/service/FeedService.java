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
    public FeedResult getRandomFeed(GetRandomFeedQuery query) {
        return randomFeedCore(query);
    }

    @CircuitBreaker(name = "matchServiceClient", fallbackMethod = "fallbackGetMatchFeed")
    @Retry(name = "matchServiceClient")
    @Transactional(readOnly = true)
    public FeedResult getMatchFeed(GetMatchFeedQuery query) {
        RecommendationResult recommendations = matchServicePort
                .getRecommendations(query.userId(), 50);
        List<UUID> recommendedUserIds = recommendations.recommendedUserIds();

        if (recommendedUserIds.isEmpty()) {
            return new FeedResult(List.of(), null, false);
        }

        CursorContext cursor = resolveCursor(query.cursor());
        List<Post> posts = postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                recommendedUserIds, List.of(Visibility.PUBLIC),
                cursor.cursorAt(), cursor.cursorId(), query.size() + 1);

        return buildFeedResult(posts, query.userId(), query.size());
    }

    public FeedResult fallbackGetMatchFeed(GetMatchFeedQuery query, Throwable t) {
        return randomFeedCore(new GetRandomFeedQuery(query.userId(), query.cursor(), query.size()));
    }

    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "fallbackGetFollowFeed")
    @Retry(name = "userServiceClient")
    @Transactional(readOnly = true)
    public FeedResult getFollowFeed(GetFollowFeedQuery query) {
        FollowingResult following = followServicePort.getFollowingIds(query.userId());
        List<UUID> followingUserIds = following.followingUserIds();

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

    public FeedResult fallbackGetFollowFeed(GetFollowFeedQuery query, Throwable t) {
        return randomFeedCore(new GetRandomFeedQuery(query.userId(), query.cursor(), query.size()));
    }

    private FeedResult randomFeedCore(GetRandomFeedQuery query) {
        CursorContext cursor = resolveCursor(query.cursorPostId());
        List<Post> posts = postRepository.findFeedByVisibilityWithCursor(
                List.of(Visibility.PUBLIC),
                cursor.cursorAt(), cursor.cursorId(), query.size() + 1);
        return buildFeedResult(posts, query.userId(), query.size());
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
