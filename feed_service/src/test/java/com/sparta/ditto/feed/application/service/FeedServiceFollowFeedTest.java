package com.sparta.ditto.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 팔로우 피드(GET /feeds/follow) 핵심 로직 단위 테스트.
 * FollowServicePort/PostRepository/LikeRepository를 Mock으로 격리하며,
 * Resilience4j(CB/Retry) 동작은 별도 통합 테스트(Phase 2)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FeedServiceFollowFeedTest {

    private static final String CLOUDFRONT_DOMAIN = "https://test.cloudfront.net";

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private MatchServicePort matchServicePort;

    @Mock
    private FollowServicePort followServicePort;

    @InjectMocks
    private FeedService feedService;

    private Post postWithScope(UUID id, UUID authorId, Visibility scope) {
        Post post = new Post(authorId, "팔로잉유저", "게시글 내용", "서울 마포구",
                37.5563, 127.0374, scope, true);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    @DisplayName("004-1: 팔로잉 사용자가 있으면 PUBLIC/FOLLOWERS_ONLY 게시글이 FeedResult로 반환된다")
    void getFollowFeed_returnsFollowingUsersPosts() {
        // given
        ReflectionTestUtils.setField(feedService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
        UUID userId = UUID.randomUUID();
        UUID followingA = UUID.randomUUID();
        UUID followingB = UUID.randomUUID();
        UUID postIdA = UUID.randomUUID();
        UUID postIdB = UUID.randomUUID();
        GetFollowFeedQuery query = new GetFollowFeedQuery(userId, null, 20);

        given(followServicePort.getFollowingIds(eq(userId)))
                .willReturn(new FollowingResult(List.of(followingA, followingB)));

        Post postA = postWithScope(postIdA, followingA, Visibility.PUBLIC);
        Post postB = postWithScope(postIdB, followingB, Visibility.FOLLOWERS_ONLY);
        given(postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                any(), any(), any(), any(), eq(21)))
                .willReturn(List.of(postA, postB));
        given(likeRepository.findPostIdsByUserIdAndPostIdIn(eq(userId), any()))
                .willReturn(List.of());

        // when
        FeedResult result = feedService.getFollowFeed(query, List.of());

        // then
        assertThat(result.feeds()).hasSize(2);
        assertThat(result.feeds().get(0).postId()).isEqualTo(postIdA);
        assertThat(result.feeds().get(1).postId()).isEqualTo(postIdB);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("004-2: 팔로잉 사용자가 없으면 빈 FeedResult를 반환하고 게시글 조회를 실행하지 않는다")
    void getFollowFeed_returnsEmptyWhenNoFollowings() {
        // given
        UUID userId = UUID.randomUUID();
        GetFollowFeedQuery query = new GetFollowFeedQuery(userId, null, 20);
        given(followServicePort.getFollowingIds(eq(userId)))
                .willReturn(new FollowingResult(List.of()));

        // when
        FeedResult result = feedService.getFollowFeed(query, List.of());

        // then
        assertThat(result.feeds()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
        verifyNoInteractions(postRepository, likeRepository);
    }
}