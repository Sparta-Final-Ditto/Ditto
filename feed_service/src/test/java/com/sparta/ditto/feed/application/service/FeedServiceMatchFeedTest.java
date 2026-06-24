package com.sparta.ditto.feed.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sparta.ditto.feed.application.dto.query.GetMatchFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
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
 * 매칭 피드(GET /feeds/match) 핵심 로직 단위 테스트.
 * MatchServicePort/PostRepository/LikeRepository를 Mock으로 격리하며,
 * Resilience4j(CB/Retry) 동작은 별도 통합 테스트(RED-2)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class 가FeedServiceMatchFeedTest {

    private static final String CLOUDFRONT_DOMAIN = "https://test.cloudfront.net";
    private static final int EXPECTED_RECOMMEND_LIMIT = 50;

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private MatchServicePort matchServicePort;

    @InjectMocks
    private FeedService feedService;

    private Post publicPost(UUID id, UUID authorId, boolean showLocation) {
        Post post = new Post(authorId, "추천유저", "추천 사용자의 게시글", "서울 강남구",
                37.4979, 127.0276, LocationScope.PUBLIC, showLocation);
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    @Test
    @DisplayName("005-1: 추천 사용자가 있으면 그들의 PUBLIC 게시글이 FeedResult로 반환된다")
    void getMatchFeed_returnsRecommendedUsersPublicPosts() {
        // given
        ReflectionTestUtils.setField(feedService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
        UUID userId = UUID.randomUUID();
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        UUID postIdA = UUID.randomUUID();
        UUID postIdB = UUID.randomUUID();
        GetMatchFeedQuery query = new GetMatchFeedQuery(userId, null, 20);

        // 추천 limit은 명세상 50
        given(matchServicePort.getRecommendations(eq(userId), eq(EXPECTED_RECOMMEND_LIMIT)))
                .willReturn(new RecommendationResult(List.of(authorA, authorB)));

        Post postA = publicPost(postIdA, authorA, true);   // showLocation=true → neighborhood 노출
        Post postB = publicPost(postIdB, authorB, false);  // showLocation=false → 005-7: neighborhood=null
        // size + 1(=21) 조회 (랜덤 피드와 동일한 페이징 규칙)
        given(postRepository.findFeedByUserIdsAndLocationScopeWithCursor(
                any(), any(), any(), any(), eq(21)))
                .willReturn(List.of(postA, postB));
        // postA만 좋아요한 상태
        given(likeRepository.findPostIdsByUserIdAndPostIdIn(eq(userId), any()))
                .willReturn(List.of(postIdA));

        // when
        FeedResult result = feedService.getMatchFeed(query);

        // then
        assertThat(result.feeds()).hasSize(2);
        assertThat(result.feeds().get(0).postId()).isEqualTo(postIdA);
        assertThat(result.feeds().get(0).isLiked()).isTrue();
        assertThat(result.feeds().get(0).neighborhood()).isEqualTo("서울 강남구");
        assertThat(result.feeds().get(1).postId()).isEqualTo(postIdB);
        assertThat(result.feeds().get(1).isLiked()).isFalse();
        assertThat(result.feeds().get(1).neighborhood()).isNull(); // 005-7
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("005-2: 추천 사용자가 0명이면 Repository를 호출하지 않고 빈 FeedResult를 반환한다")
    void getMatchFeed_returnsEmptyWhenNoRecommendations() {
        // given
        UUID userId = UUID.randomUUID();
        GetMatchFeedQuery query = new GetMatchFeedQuery(userId, null, 20);
        given(matchServicePort.getRecommendations(eq(userId), eq(EXPECTED_RECOMMEND_LIMIT)))
                .willReturn(new RecommendationResult(List.of()));

        // when
        FeedResult result = feedService.getMatchFeed(query);

        // then
        assertThat(result.feeds()).isEmpty();
        assertThat(result.nextCursor()).isNull();
        assertThat(result.hasNext()).isFalse();
        // 추천이 없으면 IN () 빈 리스트로 쿼리하지 않도록 가드 — 게시글/좋아요 조회 자체가 없어야 한다
        verifyNoInteractions(postRepository, likeRepository);
    }
}
