package com.sparta.ditto.feed.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.query.GetLikesQuery;
import com.sparta.ditto.feed.application.dto.result.LikeListResult;
import com.sparta.ditto.feed.application.dto.result.LikeListResult.LikeUserResult;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;


@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerGetLikesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostCreateFacade postCreateFacade;

    @MockBean
    private PostInteractionService postInteractionService;

    private final UUID postId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    @Test
    @DisplayName("게시글 없음 → 404, POST_NOT_FOUND")
    void getLikes_게시글없음_404_POST_NOT_FOUND() throws Exception {
        // given
        when(postInteractionService.getLikes(any(GetLikesQuery.class)))
                .thenThrow(new PostNotFoundException());

        // when & then
        mockMvc.perform(get("/posts/{postId}/likes", postId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("좋아요 목록 응답에 profileImageUrl 필드 미포함")
    void getLikes_profileImageUrl_필드미포함() throws Exception {
        // given
        LikeListResult result = new LikeListResult(
                List.of(new LikeUserResult("usr_aaa", "혼공마스터")),
                1,
                null,
                false
        );
        when(postInteractionService.getLikes(any(GetLikesQuery.class)))
                .thenReturn(result);

        // when & then
        mockMvc.perform(get("/posts/{postId}/likes", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.users[0].userId").value("usr_aaa"))
                .andExpect(jsonPath("$.data.users[0].nickname").value("혼공마스터"))
                .andExpect(jsonPath("$.data.users[0].profileImageUrl").doesNotExist());
    }
}
