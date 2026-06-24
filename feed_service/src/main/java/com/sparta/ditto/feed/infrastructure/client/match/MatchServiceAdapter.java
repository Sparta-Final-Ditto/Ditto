package com.sparta.ditto.feed.infrastructure.client.match;

import com.sparta.ditto.feed.application.port.out.MatchServicePort;
import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.infrastructure.client.match.dto.RecommendationResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchServiceAdapter implements MatchServicePort {

    private final MatchServiceClient matchServiceClient;

    @Override
    public RecommendationResult getRecommendations(UUID userId, int limit) {
        RecommendationResponse response = matchServiceClient.getRecommendations(userId, limit);
        List<UUID> userIds = response.data() == null
                ? List.of()
                : response.data().stream()
                        .map(RecommendationResponse.RecommendedUser::userId)
                        .toList();
        return new RecommendationResult(userIds);
    }
}
