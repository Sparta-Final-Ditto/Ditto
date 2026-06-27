package com.sparta.ditto.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedServiceFollowLocationTest {

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

    private UUID userId;
    private UUID followingId;
    private UUID postId;
    private GetFollowFeedQuery query;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(feedService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
        userId     = UUID.randomUUID();
        followingId = UUID.randomUUID();
        postId     = UUID.randomUUID();
        query      = new GetFollowFeedQuery(userId, null, 20);

        given(followServicePort.getFollowingIds(eq(userId)))
                .willReturn(new FollowingResult(List.of(followingId)));
        given(likeRepository.findPostIdsByUserIdAndPostIdIn(eq(userId), any()))
                .willReturn(List.of());
    }

    /** showLocation 값만 다른 게시글을 생성한다. */
    private Post postWithShowLocation(boolean showLocation) {
        Post post = new Post(followingId, "팔로잉유저", "내용", "서울 마포구",
                37.5563, 127.0374, Visibility.PUBLIC, showLocation);
        ReflectionTestUtils.setField(post, "id", postId);
        return post;
    }

    @Test
    @DisplayName("004-9: showLocation=false인 게시글은 neighborhood=null로 반환된다")
    void getFollowFeed_showLocationFalse_neighborhoodNull() {
        given(postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                any(), any(), any(), any(), eq(21)))
                .willReturn(List.of(postWithShowLocation(false)));

        FeedResult result = feedService.getFollowFeed(query);

        assertThat(result.feeds()).hasSize(1);
        assertThat(result.feeds().get(0).neighborhood()).isNull();
    }

    @Test
    @DisplayName("004-9 보완: showLocation=true인 게시글은 neighborhood가 정상 노출된다")
    void getFollowFeed_showLocationTrue_neighborhoodVisible() {
        given(postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                any(), any(), any(), any(), eq(21)))
                .willReturn(List.of(postWithShowLocation(true)));

        FeedResult result = feedService.getFollowFeed(query);

        assertThat(result.feeds()).hasSize(1);
        assertThat(result.feeds().get(0).neighborhood()).isEqualTo("서울 마포구");
    }
}