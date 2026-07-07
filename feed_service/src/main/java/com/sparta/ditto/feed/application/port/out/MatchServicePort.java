package com.sparta.ditto.feed.application.port.out;

import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import java.util.UUID;

public interface MatchServicePort {
    RecommendationResult getRecommendations(UUID userId, int limit);
}
