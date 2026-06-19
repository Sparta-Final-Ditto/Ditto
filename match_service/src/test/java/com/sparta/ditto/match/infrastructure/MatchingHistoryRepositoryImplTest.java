package com.sparta.ditto.match.infrastructure;

import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.infrastructure.jpa.MatchingHistoryJpaRepository;
import com.sparta.ditto.match.infrastructure.jpa.MatchingHistoryRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingHistoryRepositoryImplTest {

    @Mock
    private MatchingHistoryJpaRepository jpaRepository;

    @InjectMocks
    private MatchingHistoryRepositoryImpl repositoryImpl;

    @Test
    @DisplayName("save()는 JPA repository의 save를 위임 호출한다")
    void save_delegatesToJpaRepository() {
        MatchingHistory history = MatchingHistory.of(UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);
        given(jpaRepository.save(history)).willReturn(history);

        MatchingHistory result = repositoryImpl.save(history);

        assertThat(result).isEqualTo(history);
        verify(jpaRepository).save(history);
    }

    @Test
    @DisplayName("findTodayMatchByUserId()는 LocalDate를 오늘 0시 LocalDateTime으로 변환해 조회한다")
    void findTodayMatchByUserId_convertsToStartOfDay() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 6, 19);
        LocalDateTime expectedStartOfDay = LocalDateTime.of(2026, 6, 19, 0, 0, 0);
        MatchingHistory history = MatchingHistory.of(userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);

        given(jpaRepository.findTodayMatch(eq(userId), eq(expectedStartOfDay))).willReturn(Optional.of(history));

        Optional<MatchingHistory> result = repositoryImpl.findTodayMatchByUserId(userId, today);

        assertThat(result).isPresent();
        assertThat(result.get().getUserId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("findTodayMatchByUserId()는 이력이 없으면 Optional.empty를 반환한다")
    void findTodayMatchByUserId_returnsEmpty_whenNoHistory() {
        UUID userId = UUID.randomUUID();
        LocalDate today = LocalDate.now();

        given(jpaRepository.findTodayMatch(any(), any())).willReturn(Optional.empty());

        Optional<MatchingHistory> result = repositoryImpl.findTodayMatchByUserId(userId, today);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAllByUserId()는 해당 유저의 모든 매칭 이력을 반환한다")
    void findAllByUserId_returnsAllHistories() {
        UUID userId = UUID.randomUUID();
        List<MatchingHistory> histories = List.of(
                MatchingHistory.of(userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false),
                MatchingHistory.of(userId, UUID.randomUUID(), 0.7f, 0.65f, "MALE", true)
        );
        given(jpaRepository.findAllByUserId(userId)).willReturn(histories);

        List<MatchingHistory> result = repositoryImpl.findAllByUserId(userId);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findById()는 JPA repository의 findById를 위임 호출한다")
    void findById_delegatesToJpaRepository() {
        UUID id = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);
        given(jpaRepository.findById(id)).willReturn(Optional.of(history));

        Optional<MatchingHistory> result = repositoryImpl.findById(id);

        assertThat(result).isPresent();
        verify(jpaRepository).findById(id);
    }
}
