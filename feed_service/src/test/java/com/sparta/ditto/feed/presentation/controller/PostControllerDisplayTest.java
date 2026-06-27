package com.sparta.ditto.feed.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.command.UpdatePostDisplayCommand;
import com.sparta.ditto.feed.application.dto.result.UpdatePostDisplayResult;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.presentation.FeedExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({GlobalExceptionHandler.class, FeedExceptionHandler.class})
class PostControllerDisplayTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostCreateFacade postCreateFacade;

    @MockBean
    private PostInteractionService postInteractionService;

    @MockBean
    private PostService postService;

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final UUID postId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    @Test
    @DisplayName("showLocation·visibility 정상 변경 → 200 OK, message=UPDATED, DTO 직렬화 확인")
    void updatePostDisplay_정상변경_200_UPDATED() throws Exception {
        // given
        UpdatePostDisplayResult result = new UpdatePostDisplayResult(postId, false, "PRIVATE");
        when(postService.updatePostDisplay(any(UpdatePostDisplayCommand.class))).thenReturn(result);

        // when & then
        mockMvc.perform(patch("/api/v1/posts/{postId}/display", postId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showLocation\": false, \"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("UPDATED"))
                .andExpect(jsonPath("$.data.postId").value(postId.toString()))
                .andExpect(jsonPath("$.data.showLocation").value(false))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
    }

    @Test
    @DisplayName("존재하지 않는 게시글 → 404, code=POST_NOT_FOUND")
    void updatePostDisplay_게시글없음_404_POST_NOT_FOUND() throws Exception {
        // given
        when(postService.updatePostDisplay(any(UpdatePostDisplayCommand.class)))
                .thenThrow(new PostNotFoundException());

        // when & then
        mockMvc.perform(patch("/api/v1/posts/{postId}/display", postId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showLocation\": false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 → 401 Unauthorized")
    void updatePostDisplay_헤더누락_401() throws Exception {
        // when & then
        mockMvc.perform(patch("/api/v1/posts/{postId}/display", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showLocation\": false}"))
                .andExpect(status().isUnauthorized());
    }
}