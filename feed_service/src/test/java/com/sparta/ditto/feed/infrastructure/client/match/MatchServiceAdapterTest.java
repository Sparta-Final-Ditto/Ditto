package com.sparta.ditto.feed.infrastructure.client.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sparta.ditto.feed.application.port.out.dto.RecommendationResult;
import com.sparta.ditto.feed.infrastructure.client.match.dto.RecommendationResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * MatchServiceAdapter 단위 테스트.
 * MatchServiceClient를 Mock으로 격리하고 RecommendationResponse → RecommendationResult 변환 로직을 검증.
 */
@ExtendWith(MockitoExtension.class)
class MatchServiceAdapterTest {

    @Mock
    private MatchServiceClient matchServiceClient;

    @InjectMocks
    private MatchServiceAdapter matchServiceAdapter;

    @Test
    @DisplayName("match-service 정상 응답 시 추천 userId 목록이 RecommendationResult로 변환된다")
    void getRecommendations_convertsResponseToResult() {
        UUID userId = UUID.randomUUID();
        UUID recA = UUID.randomUUID();
        UUID recB = UUID.randomUUID();
        given(matchServiceClient.getRecommendations(userId, 50))
                .willReturn(new RecommendationResponse(200, "SUCCESS",
                        List.of(new RecommendationResponse.RecommendedUser(recA),
                                new RecommendationResponse.RecommendedUser(recB))));

        RecommendationResult result = matchServiceAdapter.getRecommendations(userId, 50);

        assertThat(result.recommendedUserIds()).containsExactly(recA, recB);
    }

    @Test
    @DisplayName("data가 null이면 빈 목록을 반환한다")
    void getRecommendations_returnsEmptyWhenDataNull() {
        UUID userId = UUID.randomUUID();
        given(matchServiceClient.getRecommendations(userId, 50))
                .willReturn(new RecommendationResponse(200, "SUCCESS", null));

        RecommendationResult result = matchServiceAdapter.getRecommendations(userId, 50);

        assertThat(result.recommendedUserIds()).isEmpty();
    }
}