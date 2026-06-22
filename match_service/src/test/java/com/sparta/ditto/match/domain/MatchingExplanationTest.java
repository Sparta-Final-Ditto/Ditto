package com.sparta.ditto.match.domain;

import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingExplanationTest {

    @Test
    @DisplayName("of()로 생성 시 모든 필드가 올바르게 설정된다")
    void of_setsAllFieldsCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        String explanationText = "취미와 관심사가 잘 맞는 상대입니다.";

        MatchingExplanation explanation = MatchingExplanation.of(userId, matchedUserId, explanationText);

        assertThat(explanation.getUserId()).isEqualTo(userId);
        assertThat(explanation.getMatchedUserId()).isEqualTo(matchedUserId);
        assertThat(explanation.getExplanationText()).isEqualTo(explanationText);
    }
}
