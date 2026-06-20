package com.sparta.ditto.feed.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.application.dto.request.CreatePostRequest.MediaFileRequest;
import com.sparta.ditto.feed.application.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.application.dto.response.CreatePostResponse.AuthorResponse;
import com.sparta.ditto.feed.application.dto.response.CreatePostResponse.MediaFileResponse;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final UUID postId = UUID.fromString("660e8400-e29b-41d4-a716-446655440001");

    private CreatePostResponse successResponse;

    @BeforeEach
    void setUp() {
        successResponse = new CreatePostResponse(
                postId,
                new AuthorResponse(userId, "새벽러너"),
                "오늘 새벽 러닝 완료!",
                "서울 성동구",
                List.of("#새벽운동", "#러닝"),
                List.of(new MediaFileResponse(
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
    @DisplayName("POST /posts 정상 요청 → 201, API_SPEC 응답 형식 검증")
    void createPost_정상요청_201_응답형식_검증() throws Exception {
        when(postCreateFacade.createPost(any(UUID.class), any(CreatePostRequest.class)))
                .thenReturn(successResponse);

        mockMvc.perform(post("/posts")
                        .header("X-User-Id", userId.toString())
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
}