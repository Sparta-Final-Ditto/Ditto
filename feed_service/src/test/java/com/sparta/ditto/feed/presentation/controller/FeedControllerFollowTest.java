package com.sparta.ditto.feed.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.feed.application.dto.query.GetFollowFeedQuery;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.facade.FeedFacade;
import com.sparta.ditto.feed.application.service.UploadUrlService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FeedController.class)
@Import(GlobalExceptionHandler.class)
class FeedControllerFollowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadUrlService uploadUrlService;

    @MockitoBean
    private FeedFacade feedFacade;

    private final UUID userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    @Test
    @DisplayName("004-1 Controller: 정상 요청 → 200 OK, message=SUCCESS, data.feeds 배열 반환")
    void getFollowFeed_정상요청_200() throws Exception {
        when(feedFacade.getFollowFeed(any(GetFollowFeedQuery.class)))
                .thenReturn(new FeedResult(List.of(), null, false));

        mockMvc.perform(get("/api/v1/feeds/follow")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.feeds").isArray())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 → 401 Unauthorized (FeedExceptionHandler가 MissingRequestHeaderException → COMMON-002 처리)")
    void getFollowFeed_헤더누락_401() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/follow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("size 파라미터 누락 → 기본값 20으로 FeedFacade.getFollowFeed 호출")
    void getFollowFeed_size누락_기본값20() throws Exception {
        when(feedFacade.getFollowFeed(any(GetFollowFeedQuery.class)))
                .thenReturn(new FeedResult(List.of(), null, false));

        mockMvc.perform(get("/api/v1/feeds/follow")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk());

        verify(feedFacade).getFollowFeed(argThat(q -> q.size() == 20));
    }

    @Test
    @DisplayName("size=21 최대값 초과 → 400 Bad Request")
    void getFollowFeed_size초과_400() throws Exception {
        mockMvc.perform(get("/api/v1/feeds/follow")
                        .header("X-User-Id", userId.toString())
                        .param("size", "21"))
                .andExpect(status().isBadRequest());
    }
}