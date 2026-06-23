package com.sparta.ditto.match.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.dto.MatchStatusRequestDto;
import com.sparta.ditto.match.application.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping("/today")
    public ResponseEntity<MatchResponseDto> createMatch(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody MatchRequestDto request
    ) {
        return ResponseEntity.ok(matchService.createMatch(userId, request));
    }

    @GetMapping("/today")
    public ResponseEntity<MatchResponseDto> getTodayMatch(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(matchService.getTodayMatch(userId));
    }

    @PatchMapping("/{matchId}/status")
    public ResponseEntity<ApiResponse<Void>> updateMatchStatus(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID matchId,
            @RequestBody MatchStatusRequestDto request
    ) {
        matchService.updateMatchStatus(userId, matchId, request);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/recommendations")
    public ResponseEntity<ApiResponse<List<UUID>>> getRecommendations(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        List<UUID> recommendations =
                matchService.getRecommendations(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(recommendations));
    }
}