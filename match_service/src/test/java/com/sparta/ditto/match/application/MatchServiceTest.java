package com.sparta.ditto.match.application;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.*;
import com.sparta.ditto.match.application.exception.MatchErrorCode;
import com.sparta.ditto.match.application.service.*;
import com.sparta.ditto.match.domain.entity.MatchStatus;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import com.sparta.ditto.match.infrastructure.feign.UserServiceClient;
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

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock private MatchingHistoryRepository matchingHistoryRepository;
    @Mock private MatchingBitmapService matchingBitmapService;
    @Mock private MatchingLockService matchingLockService;
    @Mock private MatchCacheService matchCacheService;
    @Mock private MatchingStatsService matchingStatsService;
    @Mock private EmbeddingServiceClient embeddingServiceClient;
    @Mock private UserServiceClient userServiceClient;
    @Mock private CosineSimilarityCalculator cosineSimilarityCalculator;
    @Mock private MatchExplanationService matchExplanationService;

    @InjectMocks
    private MatchService matchService;

    private MatchingHistory withId(MatchingHistory history) {
        try {
            Field f = MatchingHistory.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(history, UUID.randomUUID());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return history;
    }

    // ── createMatch ──────────────────────────────────────

    @Test
    @DisplayName("오늘 이미 매칭한 유저는 ALREADY_MATCHED_TODAY 예외가 발생한다")
    void createMatch_alreadyMatched_throwsException() {
        UUID userId = UUID.randomUUID();
        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(true);

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.ALREADY_MATCHED_TODAY));
    }

    @Test
    @DisplayName("락 획득 실패 시 ALREADY_MATCHING 예외가 발생한다")
    void createMatch_lockFails_throwsException() {
        UUID userId = UUID.randomUUID();
        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(false);

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.ALREADY_MATCHING));
    }

    @Test
    @DisplayName("embedding_service 호출 실패 시 EMBEDDING_SERVICE_UNAVAILABLE 예외가 발생한다")
    void createMatch_embeddingServiceFails_throwsException() {
        UUID userId = UUID.randomUUID();
        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("user_service 호출 실패 시 USER_SERVICE_UNAVAILABLE 예외가 발생한다")
    void createMatch_userServiceFails_throwsException() {
        UUID userId = UUID.randomUUID();
        float[] vector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, vector, null, true, 5);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(userServiceClient.getFollowings(userId))
                .willThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.USER_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("후보가 없으면 NO_MATCHING_CANDIDATE 예외가 발생한다")
    void createMatch_noCandidates_throwsException() {
        UUID userId = UUID.randomUUID();
        float[] vector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, vector, null, true, 5);
        ActiveUserIdsDto activeIds = new ActiveUserIdsDto(List.of(userId), 1);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(userServiceClient.getFollowings(userId))
                .willReturn(ApiResponse.success(List.of()));
        given(userServiceClient.getBlockedUsers(userId))
                .willReturn(ApiResponse.success(List.of()));
        given(embeddingServiceClient.getActiveUserIds())
                .willReturn(ApiResponse.success(activeIds));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.NO_MATCHING_CANDIDATE));
    }

    @Test
    @DisplayName("락 해제는 항상 실행된다")
    void createMatch_lockAlwaysReleased() {
        UUID userId = UUID.randomUUID();
        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willThrow(new RuntimeException("error"));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false)))
                .isInstanceOf(BusinessException.class);

        verify(matchingLockService).releaseLock(userId);
    }

    // ── getTodayMatch ──────────────────────────────────────

    @Test
    @DisplayName("오늘 매칭 이력이 없으면 MATCH_NOT_FOUND 예외가 발생한다")
    void getTodayMatch_noHistory_throwsException() {
        UUID userId = UUID.randomUUID();
        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getTodayMatch(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.MATCH_NOT_FOUND));
    }

    @Test
    @DisplayName("오늘 매칭 이력이 있으면 DTO를 반환한다")
    void getTodayMatch_hasHistory_returnsDto() {
        UUID userId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findTodayMatchByUserId(any(), any()))
                .willReturn(Optional.of(history));

        MatchResponseDto result = matchService.getTodayMatch(userId);

        assertThat(result).isNotNull();
        assertThat(result.similarityScore()).isEqualTo(0.8f);
        assertThat(result.finalScore()).isEqualTo(0.75f);
        assertThat(result.status()).isEqualTo(MatchStatus.PENDING);
    }

    // ── updateMatchStatus ──────────────────────────────────────

    @Test
    @DisplayName("매칭 이력이 없으면 MATCH_NOT_FOUND 예외가 발생한다")
    void updateMatchStatus_notFound_throwsException() {
        UUID matchId = UUID.randomUUID();
        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.updateMatchStatus(
                UUID.randomUUID(), matchId, new MatchStatusRequestDto(MatchStatus.ACCEPTED)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.MATCH_NOT_FOUND));
    }

    @Test
    @DisplayName("ACCEPTED 상태로 업데이트 시 상태가 ACCEPTED로 변경된다")
    void updateMatchStatus_accepted_changesStatus() {
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchingHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        matchService.updateMatchStatus(UUID.randomUUID(), matchId,
                new MatchStatusRequestDto(MatchStatus.ACCEPTED));

        assertThat(history.getStatus()).isEqualTo(MatchStatus.ACCEPTED);
        verify(matchingHistoryRepository).save(history);
    }

    @Test
    @DisplayName("REJECTED 상태로 업데이트 시 상태가 REJECTED로 변경된다")
    void updateMatchStatus_rejected_changesStatus() {
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchingHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        matchService.updateMatchStatus(UUID.randomUUID(), matchId,
                new MatchStatusRequestDto(MatchStatus.REJECTED));

        assertThat(history.getStatus()).isEqualTo(MatchStatus.REJECTED);
    }

    @Test
    @DisplayName("PENDING 상태는 변경되지 않는다")
    void updateMatchStatus_pendingStatus_noChange() {
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchingHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        matchService.updateMatchStatus(UUID.randomUUID(), matchId,
                new MatchStatusRequestDto(MatchStatus.PENDING));

        assertThat(history.getStatus()).isEqualTo(MatchStatus.PENDING);
        verify(matchingHistoryRepository).save(history);
    }

    // ── getRecommendations ──────────────────────────────────────

    @Test
    @DisplayName("추천 후보가 있으면 목록을 반환한다")
    void getRecommendations_withCandidates_returnsList() {
        UUID userId = UUID.randomUUID();
        String candidateId = UUID.randomUUID().toString();
        given(matchCacheService.getTopCandidates(userId, 5)).willReturn(Set.of(candidateId));

        List<RecommendationResponseDto> result = matchService.getRecommendations(userId, 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(UUID.fromString(candidateId));
    }

    @Test
    @DisplayName("추천 후보가 없으면 빈 목록을 반환한다")
    void getRecommendations_empty_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        given(matchCacheService.getTopCandidates(userId, 5)).willReturn(Set.of());

        assertThat(matchService.getRecommendations(userId, 5)).isEmpty();
    }

    @Test
    @DisplayName("추천 후보가 null이면 빈 목록을 반환한다")
    void getRecommendations_null_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        given(matchCacheService.getTopCandidates(userId, 5)).willReturn(null);

        assertThat(matchService.getRecommendations(userId, 5)).isEmpty();
    }

    @Test
    @DisplayName("추천 후보 limit 적용 확인")
    void getRecommendations_withLimit_returnsLimitedList() {
        UUID userId = UUID.randomUUID();
        Set<String> candidates = Set.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
        );

        given(matchCacheService.getTopCandidates(userId, 3)).willReturn(candidates);

        List<RecommendationResponseDto> result = matchService.getRecommendations(userId, 3);

        assertThat(result).hasSize(3);
    }

    // ── getMatchHistory ──────────────────────────────────────

    @Test
    @DisplayName("매칭 이력이 있으면 전체 목록을 반환한다")
    void getMatchHistory_hasHistory_returnsList() {
        UUID userId = UUID.randomUUID();
        MatchingHistory history1 = MatchingHistory.of(
                userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);
        MatchingHistory history2 = MatchingHistory.of(
                userId, UUID.randomUUID(), 0.7f, 0.65f, "NONE", false);

        given(matchingHistoryRepository.findAllByUserId(userId))
                .willReturn(List.of(history1, history2));

        List<MatchResponseDto> result = matchService.getMatchHistory(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).similarityScore()).isEqualTo(0.8f);
    }

    @Test
    @DisplayName("매칭 이력이 없으면 빈 목록을 반환한다")
    void getMatchHistory_noHistory_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        given(matchingHistoryRepository.findAllByUserId(userId)).willReturn(List.of());

        assertThat(matchService.getMatchHistory(userId)).isEmpty();
    }

    @Test
    @DisplayName("여러 이력이 있으면 모두 반환한다")
    void getMatchHistory_multipleHistories_returnsAll() {
        UUID userId = UUID.randomUUID();
        List<MatchingHistory> histories = List.of(
                MatchingHistory.of(userId, UUID.randomUUID(), 0.9f, 0.85f, "NONE", false),
                MatchingHistory.of(userId, UUID.randomUUID(), 0.7f, 0.65f, "MALE", true),
                MatchingHistory.of(userId, UUID.randomUUID(), 0.6f, 0.55f, "FEMALE", false)
        );

        given(matchingHistoryRepository.findAllByUserId(userId)).willReturn(histories);

        assertThat(matchService.getMatchHistory(userId)).hasSize(3);
    }

    // ── getExplanation ──────────────────────────────────────

    @Test
    @DisplayName("매칭 설명 조회 - 내 매칭이 아니면 MATCH_NOT_FOUND 예외가 발생한다")
    void getExplanation_notMyMatch_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                otherUserId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));

        assertThatThrownBy(() -> matchService.getExplanation(userId, matchId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.MATCH_NOT_FOUND));
    }

    @Test
    @DisplayName("매칭 설명 조회 - 매칭 없으면 MATCH_NOT_FOUND 예외가 발생한다")
    void getExplanation_matchNotFound_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> matchService.getExplanation(userId, matchId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.MATCH_NOT_FOUND));
    }

    @Test
    @DisplayName("매칭 설명 조회 - LLM 실패 시 EXPLANATION_GENERATION_FAILED 예외가 발생한다")
    void getExplanation_llmFails_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                userId, matchedUserId, 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchCacheService.getUserTags(any())).willReturn(Set.of());
        // float 파라미터는 anyFloat() 사용
        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willThrow(new RuntimeException("LLM 실패"));

        assertThatThrownBy(() -> matchService.getExplanation(userId, matchId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EXPLANATION_GENERATION_FAILED));
    }
}