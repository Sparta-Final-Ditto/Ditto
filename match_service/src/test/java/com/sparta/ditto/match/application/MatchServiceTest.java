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
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
    @Mock private HybridCandidateSearchService hybridCandidateSearchService;

    @InjectMocks
    private MatchService matchService;

    private MatchingHistory withId(MatchingHistory history) {
        Field f = ReflectionUtils.findField(MatchingHistory.class, "id");
        ReflectionUtils.makeAccessible(f);
        ReflectionUtils.setField(f, history, UUID.randomUUID());
        return history;
    }

    // ── createMatch ──────────────────────────────────────

    @Test
    @DisplayName("오늘 이미 매칭한 유저는 ALREADY_MATCHED_TODAY 예외가 발생한다")
    void createMatch_alreadyMatched_throwsException() {
        UUID userId = UUID.randomUUID();
        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(true);

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
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

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
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

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("HNSW 검색 결과가 있으면 해당 후보로 매칭하고 fallback은 실행하지 않는다")
    void createMatch_hnswHasResults_matchesFromHnsw() {
        UUID userId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        float[] myVector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, myVector, null, true, 5);

        LinkedHashMap<UUID, Float> hnswResults = new LinkedHashMap<>();
        hnswResults.put(candidateId, 0.87f);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(matchCacheService.getUserTags(userId)).willReturn(Set.of("여행", "커피"));

        given(hybridCandidateSearchService.searchCandidates(
                eq(userId), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(hnswResults);

        given(matchCacheService.getUserTags(candidateId)).willReturn(Set.of("커피", "영화"));
        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willReturn("공통 관심사: 커피");
        given(matchingHistoryRepository.save(any()))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        MatchResponseDto result = matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null));

        assertThat(result).isNotNull();
        assertThat(result.matchedUserId()).isEqualTo(candidateId);
        assertThat(result.similarityScore()).isEqualTo(0.87f);
        assertThat(result.status()).isEqualTo(MatchStatus.PENDING);
        assertThat(result.explanation()).isEqualTo("공통 관심사: 커피");

        // HNSW가 성공했으니 fallback(임베딩 서비스, 유저 서비스 exclude 조회)은 절대 호출되면 안 됨
        verify(embeddingServiceClient, never()).getActiveUserIds();
        verify(embeddingServiceClient, never()).getProfilesBatch(any());
        verify(hybridCandidateSearchService, never()).buildExcludeIds(any());
    }

    @Test
    @DisplayName("HNSW 결과가 여러 개면 finalScore가 가장 높은 후보를 선택한다")
    void createMatch_hnswMultipleResults_picksHighestFinalScore() {
        UUID userId = UUID.randomUUID();
        UUID lowScoreCandidate = UUID.randomUUID();
        UUID highScoreCandidate = UUID.randomUUID();
        float[] myVector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, myVector, null, true, 5);

        // LinkedHashMap 순서상 낮은 점수 후보가 먼저 오도록 구성 (순서와 무관하게 최고점 선택되는지 검증)
        LinkedHashMap<UUID, Float> hnswResults = new LinkedHashMap<>();
        hnswResults.put(lowScoreCandidate, 0.3f);
        hnswResults.put(highScoreCandidate, 0.9f);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(matchCacheService.getUserTags(userId)).willReturn(Set.of());
        given(matchCacheService.getUserTags(lowScoreCandidate)).willReturn(Set.of());
        given(matchCacheService.getUserTags(highScoreCandidate)).willReturn(Set.of());

        given(hybridCandidateSearchService.searchCandidates(
                eq(userId), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(hnswResults);

        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willReturn("설명");
        given(matchingHistoryRepository.save(any()))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        MatchResponseDto result = matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null));

        // 태그가 없어 tagScore는 0 → finalScore = cosineScore * 0.5 이므로 0.9인 쪽이 최종 승자
        assertThat(result.matchedUserId()).isEqualTo(highScoreCandidate);
        assertThat(result.similarityScore()).isEqualTo(0.9f);
    }

    @Test
    @DisplayName("HNSW 결과 없어 fallback으로 여러 후보 중 finalScore가 가장 높은 후보를 선택한다")
    void createMatch_fallbackMultipleCandidates_picksHighestFinalScore() {
        UUID userId = UUID.randomUUID();
        UUID lowScoreCandidate = UUID.randomUUID();
        UUID highScoreCandidate = UUID.randomUUID();
        float[] myVector = {0.1f, 0.2f, 0.3f};
        float[] lowVector = {0.9f, 0.0f, 0.0f};
        float[] highVector = {0.1f, 0.2f, 0.3f};

        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, myVector, null, true, 5);
        UserProfileEmbeddingDto lowCandidateProfile =
                new UserProfileEmbeddingDto(lowScoreCandidate, lowVector, null, true, 5);
        UserProfileEmbeddingDto highCandidateProfile =
                new UserProfileEmbeddingDto(highScoreCandidate, highVector, null, true, 5);

        ActiveUserIdsDto activeIds =
                new ActiveUserIdsDto(List.of(lowScoreCandidate, highScoreCandidate), 2);
        ProfileBatchResponseDto batchResponse =
                new ProfileBatchResponseDto(List.of(lowCandidateProfile, highCandidateProfile));

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(matchCacheService.getUserTags(userId)).willReturn(Set.of());
        given(matchCacheService.getUserTags(lowScoreCandidate)).willReturn(Set.of());
        given(matchCacheService.getUserTags(highScoreCandidate)).willReturn(Set.of());

        // HNSW 결과 없음 → fallback
        given(hybridCandidateSearchService.searchCandidates(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(new LinkedHashMap<>());
        given(hybridCandidateSearchService.buildExcludeIds(userId))
                .willReturn(Set.of());

        given(embeddingServiceClient.getActiveUserIds())
                .willReturn(ApiResponse.success(activeIds));
        given(embeddingServiceClient.getProfilesBatch(any()))
                .willReturn(ApiResponse.success(batchResponse));

        given(cosineSimilarityCalculator.calculate(myVector, lowVector)).willReturn(0.2f);
        given(cosineSimilarityCalculator.calculate(myVector, highVector)).willReturn(0.95f);

        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willReturn("설명");
        given(matchingHistoryRepository.save(any()))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        MatchResponseDto result = matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null));

        assertThat(result.matchedUserId()).isEqualTo(highScoreCandidate);
        assertThat(result.similarityScore()).isEqualTo(0.95f);
    }

    @Test
    @DisplayName("HNSW 결과 없어도 fallback(Feign)으로 정상 매칭된다")
    void createMatch_fallbackSuccess_matchesViaFeign() {
        UUID userId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        float[] myVector = {0.1f, 0.2f, 0.3f};
        float[] candidateVector = {0.15f, 0.25f, 0.35f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, myVector, null, true, 5);
        UserProfileEmbeddingDto candidateProfile =
                new UserProfileEmbeddingDto(candidateId, candidateVector, null, true, 3);
        ActiveUserIdsDto activeIds = new ActiveUserIdsDto(List.of(candidateId), 1);
        ProfileBatchResponseDto batchResponse =
                new ProfileBatchResponseDto(List.of(candidateProfile));

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(matchCacheService.getUserTags(userId)).willReturn(Set.of("여행", "커피"));

        // HNSW 결과 없음 → fallback 진입
        given(hybridCandidateSearchService.searchCandidates(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(new LinkedHashMap<>());
        given(hybridCandidateSearchService.buildExcludeIds(userId))
                .willReturn(Set.of());

        given(embeddingServiceClient.getActiveUserIds())
                .willReturn(ApiResponse.success(activeIds));
        given(embeddingServiceClient.getProfilesBatch(any()))
                .willReturn(ApiResponse.success(batchResponse));
        given(cosineSimilarityCalculator.calculate(any(), any())).willReturn(0.6f);
        given(matchCacheService.getUserTags(candidateId)).willReturn(Set.of("커피", "영화"));
        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willReturn("공통 관심사: 커피");
        given(matchingHistoryRepository.save(any()))
                .willAnswer(inv -> withId(inv.getArgument(0)));

        MatchResponseDto result = matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null));

        assertThat(result).isNotNull();
        assertThat(result.matchedUserId()).isEqualTo(candidateId);
        assertThat(result.similarityScore()).isEqualTo(0.6f);
        assertThat(result.explanation()).isEqualTo("공통 관심사: 커피");

        verify(matchCacheService).cacheMatchResult(eq(userId), any());
        verify(matchingBitmapService).markAsMatched(userId);
    }

    @Test
    @DisplayName("매칭 확정 후 설명 생성 실패 시 EXPLANATION_GENERATION_FAILED 예외가 발생한다")
    void createMatch_explanationGenerationFails_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        float[] myVector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, myVector, null, true, 5);

        LinkedHashMap<UUID, Float> hnswResults = new LinkedHashMap<>();
        hnswResults.put(candidateId, 0.9f);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));
        given(matchCacheService.getUserTags(userId)).willReturn(Set.of("여행"));
        given(hybridCandidateSearchService.searchCandidates(
                eq(userId), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(hnswResults);
        given(matchCacheService.getUserTags(candidateId)).willReturn(Set.of("여행"));
        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willThrow(new RuntimeException("LLM 실패"));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EXPLANATION_GENERATION_FAILED));

        // 예외가 나도 락은 반드시 해제되어야 함
        verify(matchingLockService).releaseLock(userId);
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

        // HNSW 결과 없음 → fallback(3B) 경로로 진입
        given(hybridCandidateSearchService.searchCandidates(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(new LinkedHashMap<>());
        given(hybridCandidateSearchService.buildExcludeIds(userId))
                .willReturn(Set.of());

        given(embeddingServiceClient.getActiveUserIds())
                .willReturn(ApiResponse.success(activeIds));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
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

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
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
    @DisplayName("매칭 설명 조회 - 성공 시 공통 태그 기반 설명을 반환한다")
    void getExplanation_success_returnsExplanation() {
        UUID userId = UUID.randomUUID();
        UUID matchedUserId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        MatchingHistory history = MatchingHistory.of(
                userId, matchedUserId, 0.8f, 0.75f, "NONE", false);

        given(matchingHistoryRepository.findById(matchId)).willReturn(Optional.of(history));
        given(matchCacheService.getUserTags(userId)).willReturn(Set.of("커피", "영화"));
        given(matchCacheService.getUserTags(matchedUserId)).willReturn(Set.of("커피", "등산"));
        given(matchExplanationService.generateExplanation(
                eq(userId), eq(matchedUserId), eq(List.of("커피")), eq(0.75f)))
                .willReturn("두 분 다 커피를 좋아하시네요!");

        String result = matchService.getExplanation(userId, matchId);

        assertThat(result).isEqualTo("두 분 다 커피를 좋아하시네요!");
    }

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
        // float 파라미터는 anyFloat() 사용 (any()는 primitive에 못 씀 → InvalidUseOfMatchersException)
        given(matchExplanationService.generateExplanation(any(), any(), any(), anyFloat()))
                .willThrow(new RuntimeException("LLM 실패"));

        assertThatThrownBy(() -> matchService.getExplanation(userId, matchId))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EXPLANATION_GENERATION_FAILED));
    }

    // ── createMatch fallback(3B) 세부 예외 ──────────────────────────────────

    @Test
    @DisplayName("createMatch - active 유저 목록 조회 실패 시 EMBEDDING_SERVICE_UNAVAILABLE 예외")
    void createMatch_activeUsersFails_throwsException() {
        UUID userId = UUID.randomUUID();
        float[] vector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, vector, null, true, 5);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));

        given(hybridCandidateSearchService.searchCandidates(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(new LinkedHashMap<>());
        given(hybridCandidateSearchService.buildExcludeIds(userId))
                .willReturn(Set.of());

        given(embeddingServiceClient.getActiveUserIds())
                .willThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE));
    }

    @Test
    @DisplayName("createMatch - 배치 벡터 조회 실패 시 EMBEDDING_SERVICE_UNAVAILABLE 예외")
    void createMatch_batchProfilesFails_throwsException() {
        UUID userId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        float[] vector = {0.1f, 0.2f, 0.3f};
        UserProfileEmbeddingDto myProfile =
                new UserProfileEmbeddingDto(userId, vector, null, true, 5);
        ActiveUserIdsDto activeIds = new ActiveUserIdsDto(List.of(candidateId), 1);

        given(matchingBitmapService.hasMatchedToday(userId)).willReturn(false);
        given(matchingLockService.acquireLock(userId)).willReturn(true);
        given(embeddingServiceClient.getUserProfile(userId))
                .willReturn(ApiResponse.success(myProfile));

        given(hybridCandidateSearchService.searchCandidates(
                any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(new LinkedHashMap<>());
        given(hybridCandidateSearchService.buildExcludeIds(userId))
                .willReturn(Set.of());

        given(embeddingServiceClient.getActiveUserIds())
                .willReturn(ApiResponse.success(activeIds));
        given(embeddingServiceClient.getProfilesBatch(any()))
                .willThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> matchService.createMatch(userId, new MatchRequestDto("NONE", false, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE));
    }
}
