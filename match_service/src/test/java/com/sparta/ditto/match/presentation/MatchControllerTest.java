package com.sparta.ditto.match.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.dto.MatchStatusRequestDto;
import com.sparta.ditto.match.application.dto.RecommendationResponseDto;
import com.sparta.ditto.match.application.service.MatchService;
import com.sparta.ditto.match.domain.entity.MatchStatus;
import com.sparta.ditto.match.presentation.controller.MatchController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MatchControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private MatchService matchService;

    @InjectMocks
    private MatchController matchController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(matchController).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /api/v1/matching/today - 매칭 요청 성공 시 200을 반환한다")
    void createMatch_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        MatchRequestDto request = new MatchRequestDto("NONE", false);
        MatchResponseDto response = new MatchResponseDto(
                matchId, matchedUserId, 0.8f, 0.75f,
                Instant.now(), MatchStatus.PENDING);

        given(matchService.createMatch(eq(userId), any(MatchRequestDto.class)))
                .willReturn(response);

        mockMvc.perform(post("/api/v1/matching/today")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.matchId").value(matchId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/matching/today - 오늘 매칭 조회 성공 시 200을 반환한다")
    void getTodayMatch_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        MatchResponseDto response = new MatchResponseDto(
                matchId, matchedUserId, 0.9f, 0.85f,
                Instant.now(), MatchStatus.ACCEPTED);

        given(matchService.getTodayMatch(eq(userId))).willReturn(response);

        mockMvc.perform(get("/api/v1/matching/today")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchId").value(matchId.toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/matching/{matchId}/status - 매칭 상태 업데이트 성공 시 200을 반환한다")
    void updateMatchStatus_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchStatusRequestDto request = new MatchStatusRequestDto(MatchStatus.ACCEPTED);

        mockMvc.perform(patch("/api/v1/matching/" + matchId + "/status")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getRecommendations - 추천 목록을 반환한다")
    void getRecommendations_returnsOk() {
        UUID userId = UUID.randomUUID();
        given(matchService.getRecommendations(eq(userId), anyInt())).willReturn(List.of());

        ResponseEntity<ApiResponse<List<RecommendationResponseDto>>> result =
                matchController.getRecommendations(userId, 50);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
    }
}