package com.sparta.ditto.match.domain;

import com.sparta.ditto.match.domain.entity.MatchStatus;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingHistoryTest {

    private MatchingHistory createHistory() {
        return MatchingHistory.of(
                UUID.randomUUID(), UUID.randomUUID(),
                0.9f, 0.85f, "MALE", true
        );
    }

    @Test
    @DisplayName("of()로 생성 시 모든 필드가 올바르게 설정된다")
    void of_setsAllFieldsCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();

        MatchingHistory history = MatchingHistory.of(
                userId, matchedUserId, 0.9f, 0.85f, "FEMALE", false
        );

        assertThat(history.getUserId()).isEqualTo(userId);
        assertThat(history.getMatchedUserId()).isEqualTo(matchedUserId);
        assertThat(history.getSimilarityScore()).isEqualTo(0.9f);
        assertThat(history.getFinalScore()).isEqualTo(0.85f);
        assertThat(history.getGenderFilter()).isEqualTo("FEMALE");
        assertThat(history.getMatchedAt()).isNotNull();
    }

    @Test
    @DisplayName("of()로 생성 시 초기 상태는 PENDING이다")
    void of_initialStatusIsPending() {
        MatchingHistory history = createHistory();
        assertThat(history.getStatus()).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    @DisplayName("accept() 호출 시 상태가 ACCEPTED로 변경된다")
    void accept_changesStatusToAccepted() {
        MatchingHistory history = createHistory();
        history.accept();
        assertThat(history.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
    }

    @Test
    @DisplayName("reject() 호출 시 상태가 REJECTED로 변경된다")
    void reject_changesStatusToRejected() {
        MatchingHistory history = createHistory();
        history.reject();
        assertThat(history.getStatus()).isEqualTo(MatchStatus.REJECTED);
    }
}