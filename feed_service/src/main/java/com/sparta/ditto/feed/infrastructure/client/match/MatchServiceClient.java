package com.sparta.ditto.feed.infrastructure.client.match;

import com.sparta.ditto.feed.infrastructure.client.match.dto.RecommendationResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "match-service", url = "${app.matching-service.base-url}")
public interface MatchServiceClient {

    @GetMapping("/api/v1/internal/recommendations")
    RecommendationResponse getRecommendations(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "50") int limit
    );
}
