package com.sparta.ditto.feed.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.FeedResult;
import com.sparta.ditto.feed.application.dto.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.dto.UploadUrlCommand;
import com.sparta.ditto.feed.application.dto.UploadUrlResult;
import com.sparta.ditto.feed.application.dto.UploadUrlResult.FileResult;
import com.sparta.ditto.feed.application.service.FeedService;
import com.sparta.ditto.feed.application.service.UploadUrlService;
import com.sparta.ditto.feed.presentation.dto.request.UploadUrlRequest;
import com.sparta.ditto.feed.presentation.dto.request.UploadUrlRequest.FileRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedController.class)
@Import(GlobalExceptionHandler.class)
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UploadUrlService uploadUrlService;

    @MockBean
    private FeedService feedService;

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    @DisplayName("POST /feeds/upload-url 정상 요청 → 200, presignedUrl과 s3Key 반환")
    void getUploadUrl_정상요청_200() throws Exception {
        UploadUrlRequest request = new UploadUrlRequest(
                List.of(new FileRequest("photo.jpg", "image/jpeg", 5_242_880L))
        );

        when(uploadUrlService.generateUploadUrls(any(UploadUrlCommand.class)))
                .thenReturn(new UploadUrlResult(
                        List.of(new FileResult("https://s3.example.com/presigned", "feeds/test.jpg"))
                ));

        mockMvc.perform(post("/feeds/upload-url")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.files[0].s3Key").value("feeds/test.jpg"))
                .andExpect(jsonPath("$.data.files[0].presignedUrl").value("https://s3.example.com/presigned"));
    }

    @Test
    @DisplayName("003-9: size 파라미터 누락 → 기본값 20으로 FeedService 호출")
    void tc003_9_size누락_기본값20() throws Exception {
        when(feedService.getRandomFeed(any(GetRandomFeedQuery.class)))
                .thenReturn(new FeedResult(List.of(), null, false));

        mockMvc.perform(get("/feeds/random")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        verify(feedService).getRandomFeed(
                argThat(q -> q.cursorPostId() == null && q.size() == 20)
        );
    }
}
