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

    // id 주입 헬퍼
    private MatchingHistory withId(MatchingHistory history) {
        try {
            Field idField = MatchingHistory.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(history, UUID.randomUUID());
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
    @DisplayName("정상 매칭 시 매칭 이력이 저장되고 explanation이 포함된 DTO가 반환된다")
    void createMatch_success_returnsDto() {
        UUID userId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        float[] vector = {0.1f, 0.2f, 0.3f};

        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, vector, null, true, 5);
        UserProfileEmbeddingDto candidateProfile =
                new UserProfileEmbeddingDto(candidateId, vector, null, true, 5);
        ActiveUserIdsDto activeIds = new ActiveUserIdsDto(List.of(candidateId), 1);
        ProfileBatchResponseDto batchResponse =
                new ProfileBatchResponseDto(List.of(candidateProfile));

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
        given(embeddingServiceClient.getProfilesBatch(any()))
                .willReturn(ApiResponse.success(batchResponse));
        given(matchCacheService.getUserTags(any()))
                .willReturn(Set.of("여행", "카페"));
        given(cosineSimilarityCalculator.calculate(any(), any()))
                .willReturn(0.8f);
        given(matchExplanationService.generateExplanation(any(), any(), any(), any()))
                .willReturn("잘 맞는 두 분이에요!");

        // id가 null이 안 되도록 withId로 감싸서 반환
        given(matchingHistoryRepository.save(any()))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        MatchResponseDto result = matchService.createMatch(userId, new MatchRequestDto("NONE", false));

        assertThat(result).isNotNull();
        assertThat(result.matchedUserId()).isEqualTo(candidateId);
        assertThat(result.explanation()).isEqualTo("잘 맞는 두 분이에요!");
        verify(matchingHistoryRepository).save(any());
        verify(matchingBitmapService).markAsMatched(userId);
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
        MatchingHistory history = withId(MatchingHistory.of(
                userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false));

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
        MatchingHistory history = withId(MatchingHistory.of(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false));

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
        MatchingHistory history = withId(MatchingHistory.of(
                UUID.randomUUID(), UUID.randomUUID(), 0.8f, 0.75f, "NONE", false));

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchingHistoryRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        matchService.updateMatchStatus(UUID.randomUUID(), matchId,
                new MatchStatusRequestDto(MatchStatus.REJECTED));

        assertThat(history.getStatus()).isEqualTo(MatchStatus.REJECTED);
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

    // ── getMatchHistory ──────────────────────────────────────

    @Test
    @DisplayName("매칭 이력이 있으면 전체 목록을 반환한다")
    void getMatchHistory_hasHistory_returnsList() {
        UUID userId = UUID.randomUUID();
        MatchingHistory history1 = withId(MatchingHistory.of(
                userId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false));
        MatchingHistory history2 = withId(MatchingHistory.of(
                userId, UUID.randomUUID(), 0.7f, 0.65f, "NONE", false));

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

    // ── getExplanation ──────────────────────────────────────

    @Test
    @DisplayName("매칭 설명 조회 - 정상 조회 시 설명을 반환한다")
    void getExplanation_success_returnsExplanation() {
        UUID userId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        MatchingHistory history = withId(MatchingHistory.of(
                userId, matchedUserId, 0.8f, 0.75f, "NONE", false));

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchCacheService.getUserTags(any())).willReturn(Set.of("여행", "카페"));
        given(matchExplanationService.generateExplanation(any(), any(), any(), any()))
                .willReturn("잘 맞는 두 분이에요!");

        String result = matchService.getExplanation(userId, matchId);

        assertThat(result).isEqualTo("잘 맞는 두 분이에요!");
    }

    @Test
    @DisplayName("매칭 설명 조회 - 내 매칭이 아니면 MATCH_NOT_FOUND 예외가 발생한다")
    void getExplanation_notMyMatch_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = withId(MatchingHistory.of(
                otherUserId, UUID.randomUUID(), 0.8f, 0.75f, "NONE", false));

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
}