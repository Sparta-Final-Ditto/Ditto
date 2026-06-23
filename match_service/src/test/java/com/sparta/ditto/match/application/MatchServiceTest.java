package com.sparta.ditto.match.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.dto.MatchStatusRequestDto;
import com.sparta.ditto.match.application.dto.RecommendationResponseDto;
import com.sparta.ditto.match.application.exception.MatchErrorCode;
import com.sparta.ditto.match.application.service.MatchService;
import com.sparta.ditto.match.domain.entity.MatchStatus;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import com.sparta.ditto.match.infrastructure.redis.MatchCacheService;
import com.sparta.ditto.match.infrastructure.redis.MatchingBitmapService;
import com.sparta.ditto.match.infrastructure.redis.MatchingLockService;
import com.sparta.ditto.match.infrastructure.redis.MatchingStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchingHistoryRepository matchingHistoryRepository;
    @Mock
    private MatchingBitmapService matchingBitmapService;
    @Mock
    private MatchingLockService matchingLockService;
    @Mock
    private MatchCacheService matchCacheService;
    @Mock
    private MatchingStatsService matchingStatsService;

    @InjectMocks
    private MatchService matchService;

    @Test
    @DisplayName("오늘 이미 매칭한 유저는 예외가 발생한다")
    void createMatch_alreadyMatched_throwsException() {
        UUID userId = UUID.randomUUID();
        MatchRequestDto request = new MatchRequestDto("NONE", false);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(true);

        assertThatThrownBy(() -> matchService.createMatch(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assert be.getErrorCode() == MatchErrorCode.ALREADY_MATCHED_TODAY;
                });
    }

    @Test
    @DisplayName("동시 요청 시 락 획득 실패하면 예외가 발생한다")
    void createMatch_lockFails_throwsException() {
        UUID userId = UUID.randomUUID();
        MatchRequestDto request = new MatchRequestDto("NONE", false);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(false);

        assertThatThrownBy(() -> matchService.createMatch(userId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assert be.getErrorCode() == MatchErrorCode.ALREADY_MATCHING;
                });
    }

    @Test
    @DisplayName("오늘 매칭 이력이 없으면 매칭이 진행된다")
    void createMatch_noHistory_success() {
        UUID userId = UUID.randomUUID();
        MatchRequestDto request = new MatchRequestDto("NONE", false);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(matchingHistoryRepository.save(any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        matchService.createMatch(userId, request);

        verify(matchingHistoryRepository).save(any());
    }

    @Test
    @DisplayName("오늘 매칭 이력이 없으면 getTodayMatch에서 예외가 발생한다")
    void getTodayMatch_noHistory_throwsException() {
        UUID userId = UUID.randomUUID();

        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getTodayMatch(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assert be.getErrorCode() == MatchErrorCode.MATCH_NOT_FOUND;
                });
    }

    @Test
    @DisplayName("오늘 매칭 이력이 있으면 getTodayMatch가 DTO를 반환한다")
    void getTodayMatch_hasHistory_returnsDto() {
        UUID userId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false
        );

        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.of(history));

        MatchResponseDto result = matchService.getTodayMatch(userId);

        assertThat(result).isNotNull();
        assertThat(result.similarityScore()).isEqualTo(0.8f);
        assertThat(result.finalScore()).isEqualTo(0.75f);
        assertThat(result.status()).isEqualTo(MatchStatus.PENDING);
    }

    @Test
    @DisplayName("매칭 이력이 없으면 updateMatchStatus에서 예외가 발생한다")
    void updateMatchStatus_notFound_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchStatusRequestDto request = new MatchStatusRequestDto(MatchStatus.ACCEPTED);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.updateMatchStatus(userId, matchId, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assert be.getErrorCode() == MatchErrorCode.MATCH_NOT_FOUND;
                });
    }

    @Test
    @DisplayName("ACCEPTED 상태로 updateMatchStatus 호출 시 accept()가 실행되고 저장된다")
    void updateMatchStatus_accepted_changesStatusToAccepted() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);
        MatchStatusRequestDto request = new MatchStatusRequestDto(MatchStatus.ACCEPTED);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchingHistoryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        matchService.updateMatchStatus(userId, matchId, request);

        assertThat(history.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        verify(matchingHistoryRepository).save(history);
    }

    @Test
    @DisplayName("REJECTED 상태로 updateMatchStatus 호출 시 reject()가 실행되고 저장된다")
    void updateMatchStatus_rejected_changesStatusToRejected() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);
        MatchStatusRequestDto request = new MatchStatusRequestDto(MatchStatus.REJECTED);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchingHistoryRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        matchService.updateMatchStatus(userId, matchId, request);

        assertThat(history.getStatus()).isEqualTo(MatchStatus.REJECTED);
        verify(matchingHistoryRepository).save(history);
    }

    @Test
    @DisplayName("추천 후보가 있으면 getRecommendations는 목록을 반환한다")
    void getRecommendations_withCandidates_returnsList() {
        UUID userId = UUID.randomUUID();
        String candidateId = UUID.randomUUID().toString();
        Set<String> candidates = Set.of(candidateId);

        given(matchCacheService.getTopCandidates(userId, 5)).willReturn(candidates);

        List<RecommendationResponseDto> result = matchService.getRecommendations(userId, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(UUID.fromString(candidateId));
    }

    @Test
    @DisplayName("추천 후보가 없으면 getRecommendations는 빈 목록을 반환한다")
    void getRecommendations_emptyCandidates_returnsEmptyList() {
        UUID userId = UUID.randomUUID();

        given(matchCacheService.getTopCandidates(userId, 5)).willReturn(Set.of());

        List<RecommendationResponseDto> result = matchService.getRecommendations(userId, 5);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("추천 후보가 null이면 getRecommendations는 빈 목록을 반환한다")
    void getRecommendations_nullCandidates_returnsEmptyList() {
        UUID userId = UUID.randomUUID();

        given(matchCacheService.getTopCandidates(userId, 5)).willReturn(null);

        List<RecommendationResponseDto> result = matchService.getRecommendations(userId, 5);

        assertThat(result).isEmpty();
    }
}