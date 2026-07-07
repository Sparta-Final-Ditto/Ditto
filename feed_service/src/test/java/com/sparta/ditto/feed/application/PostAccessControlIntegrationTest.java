package com.sparta.ditto.feed.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 단건 게시글 접근제어(IDOR 방지) 종단 통합 테스트.
 *
 * <p>postId만 알면 PRIVATE/FOLLOWERS_ONLY 게시글에 접근되던 IDOR을 컨트롤러→퍼사드→가드→서비스
 * 전 구간에서 차단하는지 검증한다. 접근 거부는 존재를 숨기기 위해 403이 아니라 404 POST_NOT_FOUND다.
 * 팔로우 여부는 {@link FollowServicePort}(Feign)를 {@code @MockitoBean}으로 통제한다.</p>
 */
class PostAccessControlIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private FollowServicePort followServicePort;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private Post savedPost(Visibility visibility) {
        return postRepository.save(new Post(
                owner, "작성자", "내용", "강남구",
                37.5, 127.0, visibility, true));
    }

    private ResultActions getDetail(UUID postId, UUID requester) throws Exception {
        return mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                .header("X-User-Id", requester)
                .header("X-User-Role", "USER"));
    }

    private void expectNotFound(ResultActions actions) throws Exception {
        actions.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ── PUBLIC: 누구나 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PUBLIC 게시글은 타인도 조회 가능(200)")
    void publicPost_accessibleByOther() throws Exception {
        Post post = savedPost(Visibility.PUBLIC);
        getDetail(post.getId(), other).andExpect(status().isOk());
    }

    // ── PRIVATE: 작성자만 ──────────────────────────────────────────────────

    @Test
    @DisplayName("PRIVATE 게시글은 작성자만 조회 가능(200)")
    void privatePost_accessibleByAuthor() throws Exception {
        Post post = savedPost(Visibility.PRIVATE);
        getDetail(post.getId(), owner).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PRIVATE 게시글은 타인이 조회하면 404 POST_NOT_FOUND (IDOR 차단)")
    void privatePost_deniedForOther() throws Exception {
        Post post = savedPost(Visibility.PRIVATE);
        expectNotFound(getDetail(post.getId(), other));
    }

    // ── FOLLOWERS_ONLY: 작성자 또는 팔로워만 ───────────────────────────────

    @Test
    @DisplayName("FOLLOWERS_ONLY 게시글은 팔로워가 조회 가능(200)")
    void followersOnly_accessibleByFollower() throws Exception {
        Post post = savedPost(Visibility.FOLLOWERS_ONLY);
        given(followServicePort.getFollowingIds(other))
                .willReturn(new FollowingResult(List.of(owner)));

        getDetail(post.getId(), other).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FOLLOWERS_ONLY 게시글은 비팔로워가 조회하면 404 POST_NOT_FOUND (IDOR 차단)")
    void followersOnly_deniedForNonFollower() throws Exception {
        Post post = savedPost(Visibility.FOLLOWERS_ONLY);
        given(followServicePort.getFollowingIds(other))
                .willReturn(new FollowingResult(List.of()));

        expectNotFound(getDetail(post.getId(), other));
    }

    @Test
    @DisplayName("FOLLOWERS_ONLY 게시글은 작성자 본인이 조회 가능(200) — 팔로우 확인 없이")
    void followersOnly_accessibleByAuthor() throws Exception {
        Post post = savedPost(Visibility.FOLLOWERS_ONLY);
        getDetail(post.getId(), owner).andExpect(status().isOk());
    }

    // ── 댓글·좋아요 목록도 동일 접근제어를 적용받는다 ──────────────────────

    @Test
    @DisplayName("PRIVATE 게시글의 좋아요 목록은 타인이 조회하면 404 (IDOR 차단)")
    void privatePost_likes_deniedForOther() throws Exception {
        Post post = savedPost(Visibility.PRIVATE);
        expectNotFound(mockMvc.perform(get("/api/v1/posts/{postId}/likes", post.getId())
                .header("X-User-Id", other)));
    }

    @Test
    @DisplayName("PRIVATE 게시글의 댓글 목록은 타인이 조회하면 404 (IDOR 차단)")
    void privatePost_comments_deniedForOther() throws Exception {
        Post post = savedPost(Visibility.PRIVATE);
        expectNotFound(mockMvc.perform(get("/api/v1/posts/{postId}/comments", post.getId())
                .header("X-User-Id", other)
                .header("X-User-Role", "USER")));
    }
}
