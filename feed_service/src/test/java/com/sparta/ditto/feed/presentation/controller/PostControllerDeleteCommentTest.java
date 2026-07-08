package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.facade.PostInteractionFacade;
import com.sparta.ditto.feed.application.facade.PostQueryFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.CommentNotFoundException;
import com.sparta.ditto.feed.domain.exception.ForbiddenException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerDeleteCommentTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostCreateFacade postCreateFacade;

    @MockitoBean
    private PostInteractionFacade postInteractionFacade;

    @MockitoBean
    private PostQueryFacade postQueryFacade;

    @MockitoBean
    private PostInteractionService postInteractionService;

    @MockitoBean
    private PostService postService;

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final UUID postId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private final UUID commentId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    // -------------------------------------------------------
    // 일반 USER가 타인의 댓글 삭제 시도 → 403 FORBIDDEN
    // -------------------------------------------------------
    @Test
    @DisplayName("일반 USER가 타인의 댓글 삭제 시도 → 403 FORBIDDEN")
    void deleteComment_타인댓글삭제시도_403_FORBIDDEN() throws Exception {
        // given
        doThrow(new ForbiddenException())
                .when(postInteractionService)
                .deleteComment(any(UUID.class), anyString(), any(UUID.class), any(UUID.class));

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", postId, commentId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // -------------------------------------------------------
    // 존재하지 않는 댓글 삭제 → 404 COMMENT_NOT_FOUND
    // -------------------------------------------------------
    @Test
    @DisplayName("존재하지 않는 댓글 삭제 → 404 COMMENT_NOT_FOUND")
    void deleteComment_존재하지않는댓글_404_COMMENT_NOT_FOUND() throws Exception {
        // given
        doThrow(new CommentNotFoundException())
                .when(postInteractionService)
                .deleteComment(any(UUID.class), anyString(), any(UUID.class), any(UUID.class));

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", postId, commentId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    // -------------------------------------------------------
    // 성공적인 삭제 → 200 OK, message "DELETED"
    // -------------------------------------------------------
    @Test
    @DisplayName("성공적인 댓글 삭제 → 200 OK, message DELETED")
    void deleteComment_정상삭제_200_DELETED() throws Exception {
        // given
        doNothing().when(postInteractionService)
                .deleteComment(any(UUID.class), anyString(), any(UUID.class), any(UUID.class));

        // when & then
        mockMvc.perform(delete("/api/v1/posts/{postId}/comments/{commentId}", postId, commentId)
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"));
    }
}