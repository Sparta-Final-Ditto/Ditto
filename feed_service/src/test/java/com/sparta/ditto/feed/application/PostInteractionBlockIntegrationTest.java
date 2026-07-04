package com.sparta.ditto.feed.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.feed.application.port.NotificationEventPublisher;
import com.sparta.ditto.feed.application.port.out.UserBlockPort;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.support.AbstractIntegrationTest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 좋아요/댓글 생성 흐름의 차단 검증 통합 테스트.
 *
 * <p>차단 판정(양방향)은 user-service가 하고 feed는 결과만 받으므로,
 * 이 레벨에서는 {@link UserBlockPort}를 {@code @MockitoBean}으로 모킹해 검증 결과를 통제한다.
 * "내가 작성자를 차단" / "작성자가 나를 차단" 두 방향 모두 feed 관점에서는 포트가 true를 반환하는
 * 동일한 흐름이므로, 두 방향을 @ParameterizedTest로 명시해 둔다.</p>
 */
class PostInteractionBlockIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private UserBlockPort userBlockPort;

    @MockitoBean
    private NotificationEventPublisher notificationEventPublisher;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private CommentRepository commentRepository;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private Post savedPostBy(UUID authorId) {
        return postRepository.save(new Post(
                authorId, "작성자", "내용", "강남구",
                37.5, 127.0, Visibility.PUBLIC, true));
    }

    private FeignException feignServerError() {
        Request request = Request.create(
                Request.HttpMethod.POST, "/api/v1/internal/users/chat-validation",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("validateChatUsers",
                Response.builder().status(500).reason("error").request(request)
                        .headers(Collections.emptyMap()).build());
    }

    // ── 좋아요 양방향 차단 → 403 BLOCKED_RELATION ─────────────────────────────

    @ParameterizedTest(name = "차단 방향={0}")
    @ValueSource(strings = {"내가_작성자_차단", "작성자가_나_차단"})
    @DisplayName("차단 관계 게시글 좋아요 → 403, code=BLOCKED_RELATION, 이벤트 미발행")
    void addLike_blocked_returns403(String direction) throws Exception {
        Post post = savedPostBy(ownerId);
        given(userBlockPort.isBlockedEitherDirection(requesterId, ownerId)).willReturn(true);

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", post.getId())
                        .header("X-User-Id", requesterId)
                        .header("X-User-Nickname", "요청자"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_RELATION"));

        // 차단이면 좋아요가 생성되지 않으므로 POST_LIKED 이벤트도 발행되지 않는다
        verify(notificationEventPublisher, never()).publishPostLiked(any());
    }

    // ── 댓글 양방향 차단 → 403 BLOCKED_RELATION ──────────────────────────────

    @ParameterizedTest(name = "차단 방향={0}")
    @ValueSource(strings = {"내가_작성자_차단", "작성자가_나_차단"})
    @DisplayName("차단 관계 게시글 댓글 → 403, code=BLOCKED_RELATION, 이벤트 미발행")
    void createComment_blocked_returns403(String direction) throws Exception {
        Post post = savedPostBy(ownerId);
        given(userBlockPort.isBlockedEitherDirection(requesterId, ownerId)).willReturn(true);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .header("X-User-Id", requesterId)
                        .header("X-User-Nickname", "요청자")
                        .contentType("application/json")
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_RELATION"));

        verify(notificationEventPublisher, never()).publishPostCommented(any());
    }

    // ── 차단 조회 실패 → fail-open으로 정상 생성 ──────────────────────────────

    @Test
    @DisplayName("차단 검증 예외(타임아웃/5xx) → fail-open, 좋아요 정상 생성(200)")
    void addLike_blockLookupFails_failOpen_created() throws Exception {
        Post post = savedPostBy(ownerId);
        given(userBlockPort.isBlockedEitherDirection(any(), any()))
                .willThrow(feignServerError());

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", post.getId())
                        .header("X-User-Id", requesterId)
                        .header("X-User-Nickname", "요청자"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isLiked").value(true));
    }

    @Test
    @DisplayName("차단 검증 예외 → fail-open, 댓글 정상 생성(201)")
    void createComment_blockLookupFails_failOpen_created() throws Exception {
        Post post = savedPostBy(ownerId);
        given(userBlockPort.isBlockedEitherDirection(any(), any()))
                .willThrow(feignServerError());

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .header("X-User-Id", requesterId)
                        .header("X-User-Nickname", "요청자")
                        .contentType("application/json")
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.commentId").exists());
    }

    // ── 회귀 가드: 본인 글은 차단 검증 자체를 호출하지 않음 ──────────────────────

    @Test
    @DisplayName("본인 게시글 좋아요는 차단 검증을 호출하지 않고 정상 생성")
    void addLike_ownPost_skipsBlockCheck() throws Exception {
        Post post = savedPostBy(ownerId);

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", post.getId())
                        .header("X-User-Id", ownerId)
                        .header("X-User-Nickname", "작성자"))
                .andExpect(status().isOk());

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }

    @Test
    @DisplayName("본인 게시글 댓글은 차단 검증을 호출하지 않고 정상 생성")
    void createComment_ownPost_skipsBlockCheck() throws Exception {
        Post post = savedPostBy(ownerId);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", post.getId())
                        .header("X-User-Id", ownerId)
                        .header("X-User-Nickname", "작성자")
                        .contentType("application/json")
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isCreated());

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }

    // ── 회귀 가드: 없는 게시글은 차단(403)보다 POST_NOT_FOUND(404) 우선 ─────────

    @Test
    @DisplayName("없는 게시글 좋아요는 차단 검증보다 404 POST_NOT_FOUND 우선")
    void addLike_missingPost_404_beforeBlockCheck() throws Exception {
        given(userBlockPort.isBlockedEitherDirection(any(), any())).willReturn(true);

        mockMvc.perform(post("/api/v1/posts/{postId}/likes", UUID.randomUUID())
                        .header("X-User-Id", requesterId)
                        .header("X-User-Nickname", "요청자"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }

    @Test
    @DisplayName("없는 게시글 댓글은 차단 검증보다 404 POST_NOT_FOUND 우선")
    void createComment_missingPost_404_beforeBlockCheck() throws Exception {
        given(userBlockPort.isBlockedEitherDirection(any(), any())).willReturn(true);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", UUID.randomUUID())
                        .header("X-User-Id", requesterId)
                        .header("X-User-Nickname", "요청자")
                        .contentType("application/json")
                        .content("{\"content\":\"댓글 내용\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }
}