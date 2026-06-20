package com.sparta.ditto.match.infrastructure.feign;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.UserProfileEmbeddingDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "embedding-service", url = "${embedding-service.url}")
public interface EmbeddingServiceClient {

    @GetMapping("/api/v1/internal/embedding/profile/{userId}")
    ApiResponse<UserProfileEmbeddingDto> getUserProfile(@PathVariable UUID userId);
}