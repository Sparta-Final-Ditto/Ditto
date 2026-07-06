package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.result.CommentListResult;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.query.GetCommentsQuery;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.facade.PostInteractionFacade;
import com.sparta.ditto.feed.application.facade.PostQueryFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerGetCommentsTest {

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

    private final UUID postId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");
    private final UUID requesterId = UUID.fromString("770e8400-e29b-41d4-a716-446655440002");

    @Test
    @DisplayName("게시글 없음 → 404, POST_NOT_FOUND")
    void getComments_게시글없음_404_POST_NOT_FOUND() throws Exception {
        // given
        when(postQueryFacade.getComments(any(GetCommentsQuery.class)))
                .thenThrow(new PostNotFoundException());

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", requesterId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("정상 조회 → API_SPEC 응답 규격 (isMyComment, canDelete, nextCursor, hasNext) 검증")
    void getComments_정상조회_응답_규격_검증() throws Exception {
        // given
        UUID commentId = UUID.fromString("880e8400-e29b-41d4-a716-446655440003");
        UUID commentAuthorId = UUID.fromString("990e8400-e29b-41d4-a716-446655440004");
        UUID nextCursor = UUID.fromString("aa0e8400-e29b-41d4-a716-446655440005");

        CommentResult commentResult = new CommentResult(
                commentId,
                postId,
                commentAuthorId,
                "새벽러너",
                "대단해요!",
                false,
                true,
                Instant.parse("2026-06-16T06:00:00Z")
        );
        CommentListResult listResult = new CommentListResult(
                List.of(commentResult), nextCursor, true);

        when(postQueryFacade.getComments(any(GetCommentsQuery.class)))
                .thenReturn(listResult);

        // when & then
        mockMvc.perform(get("/api/v1/posts/{postId}/comments", postId)
                        .header("X-User-Id", requesterId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.comments[0].commentId").value(commentId.toString()))
                .andExpect(jsonPath("$.data.comments[0].postId").value(postId.toString()))
                .andExpect(jsonPath("$.data.comments[0].author.userId").value(commentAuthorId.toString()))
                .andExpect(jsonPath("$.data.comments[0].author.nickname").value("새벽러너"))
                .andExpect(jsonPath("$.data.comments[0].content").value("대단해요!"))
                .andExpect(jsonPath("$.data.comments[0].isMyComment").value(false))
                .andExpect(jsonPath("$.data.comments[0].canDelete").value(true))
                .andExpect(jsonPath("$.data.nextCursor").value(nextCursor.toString()))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }
}
