package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.result.PostDetailResult;
import com.sparta.ditto.feed.application.dto.result.PostDetailResult.CommentItem;
import com.sparta.ditto.feed.application.dto.result.PostDetailResult.MediaItem;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.facade.PostInteractionFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerGetDetailTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostCreateFacade postCreateFacade;

    @MockBean
    private PostInteractionFacade postInteractionFacade;

    @MockBean
    private PostInteractionService postInteractionService;

    @MockBean
    private PostService postService;

    private final UUID authorId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final UUID otherUserId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private final UUID postId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");
    private final UUID mediaId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003");
    private final UUID commentId = UUID.fromString("990e8400-e29b-41d4-a716-446655440004");

    private final Instant commentCreatedAt = Instant.parse("2026-06-23T04:00:00Z");

    private PostDetailResult buildResult(boolean isMyPost) {
        return new PostDetailResult(
                isMyPost,
                postId,
                "오늘 날씨 참 좋네요!",
                15,
                2,
                List.of(new MediaItem(mediaId, "https://cdn.example.com/feeds/weather.png", "IMAGE", 1)),
                List.of(new CommentItem(commentId, "동감합니다!", "댓글러", commentCreatedAt, false))
        );
    }

    @BeforeEach
    void setUp() {
        when(postService.getPostDetail(any(UUID.class), any(UUID.class)))
                .thenReturn(buildResult(false));
    }

    @Test
    @DisplayName("타인의 글 조회 시 isMyPost = false")
    void getPostDetail_타인글_isMyPost_false() throws Exception {
        when(postService.getPostDetail(postId, otherUserId)).thenReturn(buildResult(false));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", otherUserId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMyPost").value(false));
    }

    @Test
    @DisplayName("본인 글 조회 시 isMyPost = true")
    void getPostDetail_본인글_isMyPost_true() throws Exception {
        when(postService.getPostDetail(postId, authorId)).thenReturn(buildResult(true));

        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", authorId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMyPost").value(true));
    }

    @Test
    @DisplayName("응답 JSON에 latitude, longitude 필드가 포함되지 않는다")
    void getPostDetail_위도경도_미포함() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", otherUserId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latitude").doesNotExist())
                .andExpect(jsonPath("$.data.longitude").doesNotExist());
    }

    @Test
    @DisplayName("댓글 목록에 commentId, content, userNickname, createdAt, isUpdated가 포함된다")
    void getPostDetail_댓글_필드_정상포함() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", otherUserId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments[0].commentId").exists())
                .andExpect(jsonPath("$.data.comments[0].content").value("동감합니다!"))
                .andExpect(jsonPath("$.data.comments[0].userNickname").value("댓글러"))
                .andExpect(jsonPath("$.data.comments[0].createdAt").exists())
                .andExpect(jsonPath("$.data.comments[0].isUpdated").value(false));
    }

    @Test
    @DisplayName("댓글 목록에 updatedAt 필드는 포함되지 않는다")
    void getPostDetail_댓글_updatedAt_미포함() throws Exception {
        mockMvc.perform(get("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", otherUserId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments[0].updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 postId 조회 시 404 POST_NOT_FOUND 반환")
    void getPostDetail_없는게시글_404() throws Exception {
        when(postService.getPostDetail(any(UUID.class), any(UUID.class)))
                .thenThrow(new PostNotFoundException());

        mockMvc.perform(get("/api/v1/posts/{postId}", UUID.randomUUID())
                        .header("X-User-Id", otherUserId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}