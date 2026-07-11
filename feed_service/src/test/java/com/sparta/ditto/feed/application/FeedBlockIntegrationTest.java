package com.sparta.ditto.feed.application;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.infrastructure.client.user.UserServiceClient;
import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockRelationsResponse;
import com.sparta.ditto.feed.support.AbstractIntegrationTest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 피드 3종(랜덤/팔로우/매칭) 차단 필터 배선 통합 테스트.
 *
 * <p>차단 관계 조회를 {@code UserBlockPort}가 아니라 <b>{@link UserServiceClient}</b>를
 * {@code @MockitoBean}으로 통제해, 실제 {@code UserBlockAdapter}(양방향 union)와
 * {@code BlockCheckService}(fail-open fallback)를 경로에 포함시킨다. 이렇게 해야
 * "내가 차단(blockedUserIds)"과 "나를 차단(blockedByUserIds)" 두 방향 제외를 통합 레벨에서
 * 의미 있게 검증할 수 있다. 외부 피드 소스({@link FollowServicePort}/{@link MatchServicePort})는
 * 계속 {@code @MockitoBean}으로 통제한다.</p>
 *
 * <p>DB에 다른 테스트가 커밋한 게시글이 남을 수 있으므로, 노출/제외는 총 개수가 아니라
 * postId 포함/미포함(hasItem/not)으로 단언한다.</p>
 */
class FeedBlockIntegrationTest extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-06-16T00:00:00Z");
    private static final String FEED_IDS = "$.data.feeds[*].postId";

    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoBean
    private FollowServicePort followServicePort;

    @MockitoBean
    private MatchServicePort matchServicePort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    private final UUID me = UUID.randomUUID();

    private enum Feed { RANDOM, FOLLOW, MATCH }

    private Post savedPublicPost(UUID author, Instant createdAt) {
        Post post = new Post(author, "작성자", "내용", "강남구",
                37.5, 127.0, Visibility.PUBLIC, true);
        org.springframework.test.util.ReflectionTestUtils.setField(post, "createdAt", createdAt);
        org.springframework.test.util.ReflectionTestUtils.setField(post, "updatedAt", createdAt);
        return postRepository.save(post);
    }

    /** block-relations 응답 스텁 팩토리(blocked=내가 차단, blockedBy=나를 차단). */
    private BlockRelationsResponse blockRelations(List<UUID> blocked, List<UUID> blockedBy) {
        return new BlockRelationsResponse(200, "SUCCESS",
                new BlockRelationsResponse.Data(blocked, blockedBy));
    }

    private FeignException feignServerError() {
        Request request = Request.create(
                Request.HttpMethod.GET, "/api/v1/internal/users/{userId}/block-relations",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("getBlockRelations",
                Response.builder().status(500).reason("error").request(request)
                        .headers(Collections.emptyMap()).build());
    }

    // ── 004-8: 팔로우 피드 양방향 차단 제외 + 빈 목록 전체노출 (차단 조회 요청당 1회) ──

    static Stream<Arguments> followBlockCases() {
        return Stream.of(
                Arguments.of("004-8 내가 차단(blockedUserIds)한 작성자 글 미노출", true, false, false),
                Arguments.of("004-8 나를 차단(blockedByUserIds)한 작성자 글 미노출", false, true, false),
                Arguments.of("004-8 차단 관계 목록 빈 경우 전체노출", false, false, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("followBlockCases")
    @DisplayName("팔로우 피드: 양방향 차단 제외/빈 목록 + 차단 조회 요청당 1회")
    void followFeed_blockFilter(
            String desc, boolean iBlockB, boolean bBlocksMe, boolean otherVisible)
            throws Exception {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        Post pb = savedPublicPost(authorB, BASE.plusSeconds(10));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA, authorB)));
        given(userServiceClient.getBlockRelations(me)).willReturn(blockRelations(
                iBlockB ? List.of(authorB) : List.of(),
                bBlocksMe ? List.of(authorB) : List.of()));

        var result = mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FEED_IDS, hasItem(pa.getId().toString())));
        if (otherVisible) {
            result.andExpect(jsonPath(FEED_IDS, hasItem(pb.getId().toString())));
        } else {
            result.andExpect(jsonPath(FEED_IDS, not(hasItem(pb.getId().toString()))));
        }

        verify(userServiceClient, times(1)).getBlockRelations(me);
    }

    @Test
    @DisplayName("004-13: 팔로잉 전원이 차단 대상 → 빈 피드(hasNext=false)")
    void followFeed_allFollowingBlocked_empty() throws Exception {
        UUID authorA = UUID.randomUUID();
        savedPublicPost(authorA, BASE.plusSeconds(20));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA)));
        given(userServiceClient.getBlockRelations(me))
                .willReturn(blockRelations(List.of(authorA), List.of()));

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // ── 005-6: 매칭 피드 양방향 차단 제외 + 추천 전원 차단 시 빈 피드 ────────────────

    static Stream<Arguments> matchBlockCases() {
        return Stream.of(
                Arguments.of("005-6 내가 차단(blockedUserIds)한 추천 작성자 글 제외", true, false),
                Arguments.of("005-6 나를 차단(blockedByUserIds)한 추천 작성자 글 제외", false, true)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matchBlockCases")
    @DisplayName("005-6: 매칭 피드 — 양방향 차단 관계 추천 작성자 글 제외")
    void matchFeed_excludesBlocked(String desc, boolean iBlockB, boolean bBlocksMe)
            throws Exception {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        Post pb = savedPublicPost(authorB, BASE.plusSeconds(10));
        given(matchServicePort.getRecommendations(eq(me), any(Integer.class)))
                .willReturn(new RecommendationResult(List.of(authorA, authorB)));
        given(userServiceClient.getBlockRelations(me)).willReturn(blockRelations(
                iBlockB ? List.of(authorB) : List.of(),
                bBlocksMe ? List.of(authorB) : List.of()));

        mockMvc.perform(get("/api/v1/feeds/match").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FEED_IDS, hasItem(pa.getId().toString())))
                .andExpect(jsonPath(FEED_IDS, not(hasItem(pb.getId().toString()))));
    }

    @Test
    @DisplayName("005-6: 추천 전원이 차단 대상 → 빈 피드")
    void matchFeed_allRecommendedBlocked_empty() throws Exception {
        UUID authorA = UUID.randomUUID();
        savedPublicPost(authorA, BASE.plusSeconds(20));
        given(matchServicePort.getRecommendations(eq(me), any(Integer.class)))
                .willReturn(new RecommendationResult(List.of(authorA)));
        given(userServiceClient.getBlockRelations(me))
                .willReturn(blockRelations(List.of(authorA), List.of()));

        mockMvc.perform(get("/api/v1/feeds/match").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds.length()").value(0));
    }

    // ── 003-6: 랜덤 피드 배선 (차단 관계 목록이 excludeUserIds 쿼리로 전달) ──────────

    @Test
    @DisplayName("003-6: 랜덤 피드 — 차단 관계 목록이 excludeUserIds 쿼리로 전달되어 제외")
    void randomFeed_wiresBlockedToExcludeQuery() throws Exception {
        UUID blockedAuthor = UUID.randomUUID();
        UUID normalAuthor = UUID.randomUUID();
        Post blockedPost = savedPublicPost(blockedAuthor, BASE.plusSeconds(20));
        Post visiblePost = savedPublicPost(normalAuthor, BASE.plusSeconds(10));
        given(userServiceClient.getBlockRelations(me))
                .willReturn(blockRelations(List.of(), List.of(blockedAuthor)));

        mockMvc.perform(get("/api/v1/feeds/random").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FEED_IDS, hasItem(visiblePost.getId().toString())))
                .andExpect(jsonPath(FEED_IDS, not(hasItem(blockedPost.getId().toString()))));
    }

    // ── fail-open: 차단 조회 실패 시 3종 모두 필터 없이 정상 노출 ─────────────────
    //   3종은 모두 BlockCheckService.blockedUserIds의 단일 fallback(빈 목록)을 거치는
    //   같은 코드 경로이므로, 피드 유형만 파라미터로 바꿔 대표 검증한다.

    @ParameterizedTest(name = "fail-open {0}")
    @EnumSource(Feed.class)
    @DisplayName("차단 조회 예외 시 피드 3종 모두 fail-open으로 정상 노출")
    void failOpen_allFeeds(Feed feed) throws Exception {
        UUID author = UUID.randomUUID();
        Post post = savedPublicPost(author, BASE.plusSeconds(20));
        given(userServiceClient.getBlockRelations(me)).willThrow(feignServerError());

        String path;
        switch (feed) {
            case FOLLOW -> {
                given(followServicePort.getFollowingIds(me))
                        .willReturn(new FollowingResult(List.of(author)));
                path = "/follow";
            }
            case MATCH -> {
                given(matchServicePort.getRecommendations(eq(me), any(Integer.class)))
                        .willReturn(new RecommendationResult(List.of(author)));
                path = "/match";
            }
            default -> path = "/random";
        }

        mockMvc.perform(get("/api/v1/feeds" + path).header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FEED_IDS, hasItem(post.getId().toString())));
    }

    // ── 회귀 구분: 피드 소스 장애 fallback(→랜덤) vs 차단 조회 fallback(→피드 유지) ──

    @Test
    @DisplayName("팔로우 소스 장애 → 랜덤 피드 우회(비팔로잉 글도 노출)로 기존 fallback 유지")
    void followSourceDown_fallsBackToRandom() throws Exception {
        UUID nonFollowed = UUID.randomUUID();
        Post randomOnly = savedPublicPost(nonFollowed, BASE.plusSeconds(20));
        given(followServicePort.getFollowingIds(me)).willThrow(new RuntimeException("down"));
        given(userServiceClient.getBlockRelations(me))
                .willReturn(blockRelations(List.of(), List.of()));

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FEED_IDS, hasItem(randomOnly.getId().toString())));
    }

    @Test
    @DisplayName("차단 조회 장애 → 팔로우 피드 유지(랜덤 우회 아님)로 정책 구분")
    void blockLookupDown_keepsFollowFeed_notRandom() throws Exception {
        UUID authorA = UUID.randomUUID();
        UUID nonFollowed = UUID.randomUUID();
        Post followed = savedPublicPost(authorA, BASE.plusSeconds(20));
        Post nonFollowedPost = savedPublicPost(nonFollowed, BASE.plusSeconds(10));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA)));
        given(userServiceClient.getBlockRelations(me)).willThrow(feignServerError());

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath(FEED_IDS, hasItem(followed.getId().toString())))
                .andExpect(jsonPath(FEED_IDS, not(hasItem(nonFollowedPost.getId().toString()))));
    }
}