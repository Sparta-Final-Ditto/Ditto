package com.sparta.ditto.feed.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.query.GetUserPostsQuery;
import com.sparta.ditto.feed.application.dto.result.UserPostsResult;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 *  GET /posts/users/{userId} 컨트롤러 슬라이스 테스트
 * - size 파라미터 누락 시 기본값 21 적용
 * - 존재하지 않는 사용자 조회 → 200 OK, 빈 목록
 * - 잘못된 UUID 형식 → 400 VALIDATION_ERROR
 */
@WebMvcTest(PostController.class)
@Import(GlobalExceptionHandler.class)
class PostControllerGetUserPostsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostCreateFacade postCreateFacade;

    @MockBean
    private PostInteractionService postInteractionService;

    @MockBean
    private PostService postService;

    private final UUID requesterId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final UUID targetUserId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    // -------------------------------------------------------
    // size 파라미터 누락 시 기본값 21 적용
    // -------------------------------------------------------
    @Test
    @DisplayName("size 파라미터 누락 시 PostService에 size=21이 전달된다")
    void getUserPosts_size_누락_기본값_21() throws Exception {
        // given
        when(postService.getUserPosts(any(GetUserPostsQuery.class)))
                .thenReturn(new UserPostsResult(List.of(), null, false));

        // when
        mockMvc.perform(get("/api/v1/posts/users/{userId}", targetUserId)
                        .header("X-User-Id", requesterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"));

        // then: service에 전달된 GetUserPostsQuery의 size가 21인지 확인
        ArgumentCaptor<GetUserPostsQuery> captor = ArgumentCaptor.forClass(GetUserPostsQuery.class);
        verify(postService).getUserPosts(captor.capture());
        assertThat(captor.getValue().size()).isEqualTo(21);
    }

    // -------------------------------------------------------
    // 존재하지 않거나 탈퇴한 사용자 → 200 OK, 빈 목록
    // -------------------------------------------------------
    @Test
    @DisplayName("존재하지 않는 userId → 200 OK, posts 빈 목록, hasNext=false 반환")
    void getUserPosts_없는_사용자_200_빈목록() throws Exception {
        // given
        UUID unknownUserId = UUID.randomUUID();
        when(postService.getUserPosts(any(GetUserPostsQuery.class)))
                .thenReturn(new UserPostsResult(List.of(), null, false));

        // when & then
        mockMvc.perform(get("/api/v1/posts/users/{userId}", unknownUserId)
                        .header("X-User-Id", requesterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.posts").isArray())
                .andExpect(jsonPath("$.data.posts").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    // -------------------------------------------------------
    // 잘못된 UUID 형식 → 400 VALIDATION_ERROR
    // -------------------------------------------------------
    @Test
    @DisplayName("잘못된 UUID 형식의 userId → 400 Bad Request, code=VALIDATION_ERROR")
    void getUserPosts_잘못된UUID_400_VALIDATION_ERROR() throws Exception {
        // when & then
        // Spring이 "not-a-uuid"를 UUID 타입으로 변환하지 못할 때 400 VALIDATION_ERROR 반환
        mockMvc.perform(get("/api/v1/posts/users/{userId}", "not-a-uuid")
                        .header("X-User-Id", requesterId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
