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
import com.sparta.ditto.feed.application.port.out.UserBlockPort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.support.AbstractIntegrationTest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 피드 3종(랜덤/팔로우/매칭) 차단 필터 배선 통합 테스트.
 *
 * <p>차단 목록 조회({@link UserBlockPort#findBlockedUserIds})와 외부 피드 소스
 * ({@link FollowServicePort}/{@link MatchServicePort})를 {@code @MockitoBean}으로 통제한다.
 * DB에는 다른 테스트가 커밋한 게시글이 남을 수 있으므로, 노출/제외는 총 개수가 아니라
 * postId의 포함/미포함(hasItem/not)으로 단언한다.</p>
 */
class FeedBlockIntegrationTest extends AbstractIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-06-16T00:00:00Z");

    @MockitoBean
    private UserBlockPort userBlockPort;

    @MockitoBean
    private FollowServicePort followServicePort;

    @MockitoBean
    private MatchServicePort matchServicePort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private final UUID me = UUID.randomUUID();

    /**
     * 서킷 상태는 레지스트리(싱글턴 빈)에 남아 테스트 간 누수된다.
     * fail-open 테스트가 userServiceClient/matchServiceClient 서킷을 OPEN 시키면
     * 이후 테스트가 오염되므로, 각 테스트 전에 CLOSED로 리셋한다.
     */
    @BeforeEach
    void resetCircuitBreakers() {
        circuitBreakerRegistry.circuitBreaker("userServiceClient").reset();
        circuitBreakerRegistry.circuitBreaker("matchServiceClient").reset();
    }

    private Post savedPublicPost(UUID author, Instant createdAt) {
        Post post = new Post(author, "작성자", "내용", "강남구",
                37.5, 127.0, Visibility.PUBLIC, true);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", createdAt);
        return postRepository.save(post);
    }

    private FeignException feignServerError() {
        Request request = Request.create(
                Request.HttpMethod.GET, "/api/v1/users/me/blocks",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("getMyBlocks",
                Response.builder().status(500).reason("error").request(request)
                        .headers(Collections.emptyMap()).build());
    }

    // ── 팔로우 피드 (004-8, 004-13) ───────────────────────────────────────────

    @Test
    @DisplayName("팔로우 피드: 내가 차단한 작성자 글 제외, 차단 조회는 요청당 1회")
    void followFeed_excludesBlocked_lookupOnce() throws Exception {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        Post pb = savedPublicPost(authorB, BASE.plusSeconds(10));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA, authorB)));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of(authorB));

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(pa.getId().toString())))
                .andExpect(jsonPath("$.data.feeds[*].postId", not(hasItem(pb.getId().toString()))));

        verify(userBlockPort, times(1)).findBlockedUserIds(me);
    }

    @Test
    @DisplayName("팔로우 피드: 차단 목록이 비면 팔로잉 글 전체 노출")
    void followFeed_emptyBlocked_showsAll() throws Exception {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        Post pb = savedPublicPost(authorB, BASE.plusSeconds(10));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA, authorB)));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(pa.getId().toString())))
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(pb.getId().toString())));
    }

    @Test
    @DisplayName("팔로우 피드: 팔로잉 전원이 차단 대상 → 빈 피드(hasNext=false)")
    void followFeed_allFollowingBlocked_empty() throws Exception {
        UUID authorA = UUID.randomUUID();
        savedPublicPost(authorA, BASE.plusSeconds(20));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA)));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of(authorA));

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // ── 매칭 피드 (005-6) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("매칭 피드: 내가 차단한 추천 작성자 글 제외")
    void matchFeed_excludesBlocked() throws Exception {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        Post pb = savedPublicPost(authorB, BASE.plusSeconds(10));
        given(matchServicePort.getRecommendations(eq(me), any(Integer.class)))
                .willReturn(new RecommendationResult(List.of(authorA, authorB)));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of(authorB));

        mockMvc.perform(get("/api/v1/feeds/match").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(pa.getId().toString())))
                .andExpect(jsonPath("$.data.feeds[*].postId", not(hasItem(pb.getId().toString()))));
    }

    @Test
    @DisplayName("매칭 피드: 추천 전원이 차단 대상 → 빈 피드")
    void matchFeed_allRecommendedBlocked_empty() throws Exception {
        UUID authorA = UUID.randomUUID();
        savedPublicPost(authorA, BASE.plusSeconds(20));
        given(matchServicePort.getRecommendations(eq(me), any(Integer.class)))
                .willReturn(new RecommendationResult(List.of(authorA)));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of(authorA));

        mockMvc.perform(get("/api/v1/feeds/match").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds.length()").value(0));
    }

    // ── 랜덤 피드 배선 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("랜덤 피드: 차단 목록이 excludeUserIds 쿼리로 전달되어 제외된다")
    void randomFeed_wiresBlockedToExcludeQuery() throws Exception {
        UUID blockedAuthor = UUID.randomUUID();
        UUID normalAuthor = UUID.randomUUID();
        Post blockedPost = savedPublicPost(blockedAuthor, BASE.plusSeconds(20));
        Post visiblePost = savedPublicPost(normalAuthor, BASE.plusSeconds(10));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of(blockedAuthor));

        mockMvc.perform(get("/api/v1/feeds/random").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(visiblePost.getId().toString())))
                .andExpect(jsonPath("$.data.feeds[*].postId", not(hasItem(blockedPost.getId().toString()))));
    }

    // ── fail-open: 차단 조회 실패 시 3종 모두 필터 없이 정상 노출 ─────────────────

    @Test
    @DisplayName("fail-open: 차단 조회 예외 시 랜덤 피드 정상 노출")
    void failOpen_randomFeed() throws Exception {
        UUID author = UUID.randomUUID();
        Post post = savedPublicPost(author, BASE.plusSeconds(20));
        given(userBlockPort.findBlockedUserIds(me)).willThrow(feignServerError());

        mockMvc.perform(get("/api/v1/feeds/random").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(post.getId().toString())));
    }

    @Test
    @DisplayName("fail-open: 차단 조회 예외 시 팔로우 피드 정상 노출")
    void failOpen_followFeed() throws Exception {
        UUID authorA = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        given(followServicePort.getFollowingIds(me))
                .willReturn(new FollowingResult(List.of(authorA)));
        given(userBlockPort.findBlockedUserIds(me)).willThrow(feignServerError());

        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(pa.getId().toString())));
    }

    @Test
    @DisplayName("fail-open: 차단 조회 예외 시 매칭 피드 정상 노출")
    void failOpen_matchFeed() throws Exception {
        UUID authorA = UUID.randomUUID();
        Post pa = savedPublicPost(authorA, BASE.plusSeconds(20));
        given(matchServicePort.getRecommendations(eq(me), any(Integer.class)))
                .willReturn(new RecommendationResult(List.of(authorA)));
        given(userBlockPort.findBlockedUserIds(me)).willThrow(feignServerError());

        mockMvc.perform(get("/api/v1/feeds/match").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(pa.getId().toString())));
    }

    // ── 회귀 구분: 피드 소스 장애 fallback(→랜덤) vs 차단 조회 fallback(→피드 유지) ──

    @Test
    @DisplayName("팔로우 소스 장애 → 랜덤 피드 우회(비팔로잉 글도 노출)로 기존 fallback 유지")
    void followSourceDown_fallsBackToRandom() throws Exception {
        UUID nonFollowed = UUID.randomUUID();
        Post randomOnly = savedPublicPost(nonFollowed, BASE.plusSeconds(20));
        given(followServicePort.getFollowingIds(me)).willThrow(new RuntimeException("user-service down"));
        given(userBlockPort.findBlockedUserIds(me)).willReturn(List.of());

        // 팔로우하지 않은 작성자의 글이 노출되면 = 랜덤 피드로 우회된 것
        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(randomOnly.getId().toString())));
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
        given(userBlockPort.findBlockedUserIds(me)).willThrow(feignServerError());

        // 팔로잉 글은 노출되고, 비팔로잉 글은 노출되지 않음 = 랜덤 우회가 아니라 팔로우 피드 유지
        mockMvc.perform(get("/api/v1/feeds/follow").header("X-User-Id", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[*].postId", hasItem(followed.getId().toString())))
                .andExpect(jsonPath("$.data.feeds[*].postId", not(hasItem(nonFollowedPost.getId().toString()))));
    }
}