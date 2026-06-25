package com.sparta.ditto.match.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.dto.MatchStatusRequestDto;
import com.sparta.ditto.match.application.dto.RecommendationResponseDto;
import com.sparta.ditto.match.application.service.MatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name ="매칭" , description = "매칭 API")
@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "오늘의 매칭 생성")
    @PostMapping("/today")
    public ResponseEntity<MatchResponseDto> createMatch(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody MatchRequestDto request
    ) {
        return ResponseEntity.ok(matchService.createMatch(userId, request));
    }

    @Operation(summary = "오늘의 매칭 조회")
    @GetMapping("/today")
    public ResponseEntity<MatchResponseDto> getTodayMatch(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(matchService.getTodayMatch(userId));
    }

    @Operation(summary = "매칭 사태 업데이트 (수락 / 거절)")
    @PatchMapping("/{matchId}/status")
    public ResponseEntity<ApiResponse<Void>> updateMatchStatus(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID matchId,
            @RequestBody MatchStatusRequestDto request
    ) {
        matchService.updateMatchStatus(userId, matchId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @Operation(summary = "추천 유저 목록 조회")
    public ResponseEntity<ApiResponse<List<RecommendationResponseDto>>> getRecommendations(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "50") int limit  // default 20 → 50
    ) {
        List<RecommendationResponseDto> recommendations =
                matchService.getRecommendations(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
}