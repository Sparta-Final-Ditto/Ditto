package com.sparta.ditto.feed.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.result.PostResult;
import com.sparta.ditto.feed.application.dto.result.PostResult.MediaFileResult;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.facade.PostInteractionFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import com.sparta.ditto.feed.domain.exception.LikeNotFoundException;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostCreateFacade postCreateFacade;

    @MockBean
    private PostInteractionFacade postInteractionFacade;

    @MockBean
    private PostInteractionService postInteractionService;

    @MockBean
    private PostService postService;

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final UUID postId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private PostResult successResult;

    @BeforeEach
    void setUp() {
        successResult = new PostResult(
                postId,
                userId,
                "새벽러너",
                "오늘 새벽 러닝 완료!",
                "서울 성동구",
                List.of("#새벽운동", "#러닝"),
                List.of(new MediaFileResult(
                        "feeds/test-uuid.mp4",
                        "https://cdn.example.com/feeds/test-uuid.mp4",
                        "VIDEO",
                        1
                )),
                0,
                false,
                0,
                true,
                Instant.parse("2026-06-16T05:30:00Z")
        );
    }

    private String validRequestBody() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "오늘 새벽 러닝 완료!",
                List.of("#새벽운동", "#러닝"),
                37.5563,
                127.0374,
                "PUBLIC",
                true,
                List.of(new MediaFileRequest("feeds/test-uuid.mp4", "VIDEO", 1))
        );
        return objectMapper.writeValueAsString(request);
    }

    @Test
    @DisplayName("존재하지 않는 게시글 좋아요 → 404, POST_NOT_FOUND")
    void addLike_게시글없음_404_POST_NOT_FOUND() throws Exception {
        // given
        when(postInteractionFacade.addLike(any(UUID.class), any(UUID.class), anyString()))
                .thenThrow(new PostNotFoundException());

        // when & then
        mockMvc.perform(post("/api/v1/posts/{postId}/likes", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "테스트닉네임"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-008-7: 게시글 존재하지 않음 → 404, POST_NOT_FOUND")
    void removeLike_게시글없음_404_POST_NOT_FOUND() throws Exception {
        // given
        when(postInteractionService.removeLike(any(UUID.class), any(UUID.class)))
                .thenThrow(new PostNotFoundException());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", postId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-008-2: 좋아요하지 않은 게시글 취소 → 404, LIKE_NOT_FOUND")
    void removeLike_좋아요없음_404_LIKE_NOT_FOUND() throws Exception {
        // given
        when(postInteractionService.removeLike(any(UUID.class), any(UUID.class)))
                .thenThrow(new LikeNotFoundException());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}/likes", postId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("LIKE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /posts 정상 요청 → 201, API_SPEC 응답 형식 검증")
    void createPost_정상요청_201_응답형식_검증() throws Exception {
        when(postCreateFacade.createPost(any(CreatePostCommand.class)))
                .thenReturn(successResult);

        mockMvc.perform(post("/api/v1/posts")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "새벽러너")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("CREATED"))
                .andExpect(jsonPath("$.data.postId").value(postId.toString()))
                .andExpect(jsonPath("$.data.author.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.author.nickname").value("새벽러너"))
                .andExpect(jsonPath("$.data.content").value("오늘 새벽 러닝 완료!"))
                .andExpect(jsonPath("$.data.neighborhood").value("서울 성동구"))
                .andExpect(jsonPath("$.data.tags[0]").value("#새벽운동"))
                .andExpect(jsonPath("$.data.mediaFiles[0].s3Key").value("feeds/test-uuid.mp4"))
                .andExpect(jsonPath("$.data.mediaFiles[0].mediaUrl").value("https://cdn.example.com/feeds/test-uuid.mp4"))
                .andExpect(jsonPath("$.data.mediaFiles[0].mediaType").value("VIDEO"))
                .andExpect(jsonPath("$.data.mediaFiles[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.likeCount").value(0))
                .andExpect(jsonPath("$.data.isLiked").value(false))
                .andExpect(jsonPath("$.data.commentCount").value(0))
                .andExpect(jsonPath("$.data.showLocation").value(true))
                .andExpect(jsonPath("$.data.createdAt").value("2026-06-16T05:30:00Z"))
                .andExpect(jsonPath("$.data.latitude").doesNotExist())
                .andExpect(jsonPath("$.data.longitude").doesNotExist());
    }

    // ============================================================
    // POST /posts/{postId}/comments (댓글 등록)
    // ============================================================

    @Test
    @DisplayName("정상 요청 → 201 CREATED, commentId 반환")
    void createComment_정상요청_201_commentId_반환() throws Exception {
        UUID commentId = UUID.randomUUID();
        CommentResult commentResult = new CommentResult(
                commentId,
                postId,
                userId,
                "닉네임",
                "댓글 내용",
                true,
                true,
                Instant.now()
        );
        when(postInteractionFacade.createComment(any(UUID.class), anyString(), any(UUID.class), any(CreateCommentCommand.class)))
                .thenReturn(commentResult);

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "닉네임")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"댓글 내용\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("CREATED"))
                .andExpect(jsonPath("$.data.commentId").value(commentId.toString()));
    }

    @Test
    @DisplayName("content 누락 → 400, COMMON-001")
    void createComment_content_누락_400_COMMON_001() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "닉네임")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    @DisplayName("공백만 입력 → 400, COMMON-001")
    void createComment_공백입력_400_COMMON_001() throws Exception {
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "닉네임")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    @DisplayName("201자 입력 → 400, COMMON-001")
    void createComment_201자_입력_400_COMMON_001() throws Exception {
        String over200 = "a".repeat(201);
        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "닉네임")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + over200 + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    @DisplayName("없는 postId → 404, POST_NOT_FOUND")
    void createComment_없는postId_404_POST_NOT_FOUND() throws Exception {
        when(postInteractionFacade.createComment(
                any(UUID.class), anyString(), any(UUID.class), any()))
                .thenThrow(new PostNotFoundException());

        mockMvc.perform(post("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Nickname", "닉네임")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"댓글 내용\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // ============================================================
    // DELETE /posts/{postId} (게시글 삭제)
    // ============================================================

    @Test
    @DisplayName("작성자가 자신의 게시글 삭제 → 200 OK, message DELETED")
    void deletePost_정상삭제_200_DELETED() throws Exception {
        // given
        doNothing().when(postService).deletePost(any(UUID.class), any(UUID.class), anyString());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("DELETED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("없는 게시글 삭제 → 404 POST_NOT_FOUND")
    void deletePost_없는게시글_404_POST_NOT_FOUND() throws Exception {
        // given
        doThrow(new PostNotFoundException())
                .when(postService).deletePost(any(UUID.class), any(UUID.class), anyString());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("권한 없는 USER 삭제 → 403 FORBIDDEN")
    void deletePost_권한없음_403_FORBIDDEN() throws Exception {
        // given
        doThrow(new ForbiddenException())
                .when(postService).deletePost(any(UUID.class), any(UUID.class), anyString());

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}", postId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
