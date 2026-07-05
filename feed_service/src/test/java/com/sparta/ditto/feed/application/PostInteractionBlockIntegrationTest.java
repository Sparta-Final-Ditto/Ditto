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
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.support.AbstractIntegrationTest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 좋아요/댓글 생성 흐름의 차단 검증 통합 테스트.
 *
 * <p>차단 판정(양방향)은 user-service가 하고 feed는 결과만 받으므로 {@link UserBlockPort}를
 * {@code @MockitoBean}으로 통제한다. 좋아요·댓글, "내가 작성자 차단"·"작성자가 나 차단"은
 * feed 관점에서 포트가 true를 반환하는 동일 흐름이라 @ParameterizedTest로 통합한다.</p>
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

    private final UUID requesterId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private enum Interaction { LIKE, COMMENT }

    private Post savedPostBy(UUID authorId) {
        return postRepository.save(new Post(
                authorId, "작성자", "내용", "강남구",
                37.5, 127.0, Visibility.PUBLIC, true));
    }

    private ResultActions perform(Interaction type, UUID postId, UUID actorId) throws Exception {
        if (type == Interaction.LIKE) {
            return mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                    .header("X-User-Id", actorId)
                    .header("X-User-Nickname", "요청자"));
        }
        return mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                .header("X-User-Id", actorId)
                .header("X-User-Nickname", "요청자")
                .contentType("application/json")
                .content("{\"content\":\"댓글 내용\"}"));
    }

    private void verifyNoNotification(Interaction type) {
        if (type == Interaction.LIKE) {
            verify(notificationEventPublisher, never()).publishPostLiked(any());
        } else {
            verify(notificationEventPublisher, never()).publishPostCommented(any());
        }
    }

    private FeignException feignServerError() {
        Request request = Request.create(
                Request.HttpMethod.POST, "/api/v1/internal/users/chat-validation",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("validateChatUsers",
                Response.builder().status(500).reason("error").request(request)
                        .headers(Collections.emptyMap()).build());
    }

    // ── 007-4 / 010-6: 양방향 차단 → 403 BLOCKED_RELATION + 이벤트 미발행 ─────────

    static Stream<Arguments> blockedCases() {
        return Stream.of(
                Arguments.of("007-4 좋아요/내가 작성자 차단", Interaction.LIKE),
                Arguments.of("007-4 좋아요/작성자가 나 차단", Interaction.LIKE),
                Arguments.of("010-6 댓글/내가 작성자 차단", Interaction.COMMENT),
                Arguments.of("010-6 댓글/작성자가 나 차단", Interaction.COMMENT)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blockedCases")
    @DisplayName("차단 관계 → 403, code=BLOCKED_RELATION, 알림 이벤트 미발행")
    void blocked_returns403(String desc, Interaction type) throws Exception {
        Post post = savedPostBy(ownerId);
        given(userBlockPort.isBlockedEitherDirection(requesterId, ownerId)).willReturn(true);

        perform(type, post.getId(), requesterId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BLOCKED_RELATION"));

        verifyNoNotification(type);
    }

    // ── 007-9 / 010-11: 차단 조회 실패 → fail-open으로 정상 생성 ──────────────────

    @ParameterizedTest(name = "{0} fail-open 정상 생성")
    @EnumSource(Interaction.class)
    @DisplayName("차단 검증 예외(타임아웃/5xx) → fail-open, 정상 생성(2xx)")
    void blockLookupFails_failOpen_created(Interaction type) throws Exception {
        Post post = savedPostBy(ownerId);
        given(userBlockPort.isBlockedEitherDirection(any(), any())).willThrow(feignServerError());

        perform(type, post.getId(), requesterId)
                .andExpect(status().is2xxSuccessful());
    }

    // ── 회귀 가드: 본인 글은 차단 검증 자체를 호출하지 않음 ──────────────────────

    @Test
    @DisplayName("본인 게시글 좋아요는 차단 검증을 호출하지 않고 정상 생성")
    void addLike_ownPost_skipsBlockCheck() throws Exception {
        Post post = savedPostBy(ownerId);

        perform(Interaction.LIKE, post.getId(), ownerId).andExpect(status().isOk());

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }

    @Test
    @DisplayName("본인 게시글 댓글은 차단 검증을 호출하지 않고 정상 생성")
    void createComment_ownPost_skipsBlockCheck() throws Exception {
        Post post = savedPostBy(ownerId);

        perform(Interaction.COMMENT, post.getId(), ownerId).andExpect(status().isCreated());

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }

    // ── 회귀 가드: 없는 게시글은 차단(403)보다 POST_NOT_FOUND(404) 우선 ─────────

    @Test
    @DisplayName("없는 게시글 좋아요는 차단 검증보다 404 POST_NOT_FOUND 우선")
    void addLike_missingPost_404_beforeBlockCheck() throws Exception {
        given(userBlockPort.isBlockedEitherDirection(any(), any())).willReturn(true);

        perform(Interaction.LIKE, UUID.randomUUID(), requesterId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }

    @Test
    @DisplayName("없는 게시글 댓글은 차단 검증보다 404 POST_NOT_FOUND 우선")
    void createComment_missingPost_404_beforeBlockCheck() throws Exception {
        given(userBlockPort.isBlockedEitherDirection(any(), any())).willReturn(true);

        perform(Interaction.COMMENT, UUID.randomUUID(), requesterId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        verify(userBlockPort, never()).isBlockedEitherDirection(any(), any());
    }
}
