package com.sparta.ditto.match.presentation.controller;

import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    // 하루 1회 매칭 요청
    @PostMapping("/today")
    public ResponseEntity<MatchResponseDto> createMatch(
            @RequestHeader("X-User-Id") UUID userId,  // Gateway에서 전달
            @RequestBody MatchRequestDto request
    ) {
        return ResponseEntity.ok(matchService.createMatch(userId, request));
    }

    // 오늘 매칭 결과 조회
    @GetMapping("/today")
    public ResponseEntity<MatchResponseDto> getTodayMatch(
            @RequestHeader("X-User-Id") UUID userId
    ) {
        return ResponseEntity.ok(matchService.getTodayMatch(userId));
    }
}