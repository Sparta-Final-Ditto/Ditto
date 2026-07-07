package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.query.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedItemResult;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 조회의 <b>DB 트랜잭션 계층</b>.
 *
 * <p>외부 서비스(match/user) 호출은 이 서비스에 존재하지 않는다. 매칭/팔로우 피드의 외부 호출과
 * Resilience4j 처리는 {@link com.sparta.ditto.feed.application.facade.FeedSourceFacade}가
 * 트랜잭션 밖에서 수행하고, 확정된 사용자 ID 목록만 이 서비스의 {@code @Transactional} 메서드로
 * 전달한다. 따라서 외부 호출 지연이 DB 커넥션 점유로 이어지지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Transactional(readOnly = true)
    public FeedResult getRandomFeed(GetRandomFeedQuery query, List<UUID> blockedIds) {
        return randomFeedCore(query, blockedIds);
    }

    /**
     * 확정된 사용자 ID 목록의 게시글을 조회한다(매칭/팔로우 피드 공용 DB 조회).
     *
     * <p>외부 호출은 이 메서드에 진입하기 전(트랜잭션 밖)에 이미 완료되어 있어야 한다.
     * 소스 사용자 ID가 비면 IN () 빈 쿼리를 피하기 위해 DB 조회 없이 빈 결과를 반환한다.</p>
     */
    @Transactional(readOnly = true)
    public FeedResult getFeedByUserIds(UUID userId, List<UUID> userIds,
            List<Visibility> visibilities, UUID cursorPostId, int size) {
        if (userIds.isEmpty()) {
            return new FeedResult(List.of(), null, false);
        }

        CursorContext cursor = resolveCursor(cursorPostId);
        List<Post> posts = postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                userIds, visibilities, cursor.cursorAt(), cursor.cursorId(), size + 1);

        return buildFeedResult(posts, userId, size);
    }

    private FeedResult randomFeedCore(GetRandomFeedQuery query, List<UUID> blockedIds) {
        CursorContext cursor = resolveCursor(query.cursorPostId());
        // 빈/null 분기는 RepositoryImpl 책임 — Service는 항상 제외 쿼리로 위임한다.
        List<Post> posts = postRepository.findFeedByVisibilityExcludingAuthorsWithCursor(
                List.of(Visibility.PUBLIC), blockedIds,
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