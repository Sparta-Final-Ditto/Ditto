package com.sparta.ditto.match.infrastructure.feign;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.ActiveUserIdsDto;
import com.sparta.ditto.match.application.dto.EmbedTextRequestDto;
import com.sparta.ditto.match.application.dto.EmbedTextResponseDto;
import com.sparta.ditto.match.application.dto.ProfileBatchRequestDto;
import com.sparta.ditto.match.application.dto.ProfileBatchResponseDto;
import com.sparta.ditto.match.application.dto.UserProfileEmbeddingDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

// infrastructure/feign/EmbeddingServiceClient.java
@FeignClient(name = "embedding-service", url = "${embedding-service.url}")
public interface EmbeddingServiceClient {

    // 기존
    @GetMapping("/api/v1/internal/embedding/profile/{userId}")
    ApiResponse<UserProfileEmbeddingDto> getUserProfile(
            @PathVariable UUID userId
    );

    // 추가 1 - active 유저 ID 목록
    @GetMapping("/api/v1/internal/embedding/profiles/active/ids")
    ApiResponse<ActiveUserIdsDto> getActiveUserIds();

    // 추가 2 - 배치 벡터 조회
    @PostMapping("/api/v1/internal/embedding/profiles/batch")
    ApiResponse<ProfileBatchResponseDto> getProfilesBatch(
            @RequestBody ProfileBatchRequestDto request
    );

    // 추가 3 - 임의 텍스트 임베딩 (매칭 설명 RAG용)
    @PostMapping("/api/v1/internal/embedding/embed-text")
    ApiResponse<EmbedTextResponseDto> embedText(
            @RequestBody EmbedTextRequestDto request
    );
}