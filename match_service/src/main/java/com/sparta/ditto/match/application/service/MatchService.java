package com.sparta.ditto.match.application.service;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.match.application.dto.ActiveUserIdsDto;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.dto.MatchStatusRequestDto;
import com.sparta.ditto.match.application.dto.ProfileBatchRequestDto;
import com.sparta.ditto.match.application.dto.ProfileBatchResponseDto;
import com.sparta.ditto.match.application.dto.RecommendationResponseDto;
import com.sparta.ditto.match.application.dto.UserNeighborhoodDto;
import com.sparta.ditto.match.application.dto.UserProfileEmbeddingDto;
import com.sparta.ditto.match.application.exception.MatchErrorCode;
import com.sparta.ditto.match.domain.entity.MatchStatus;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import com.sparta.ditto.match.infrastructure.feign.UserServiceClient;
import com.sparta.ditto.match.infrastructure.redis.MatchCacheService;
import com.sparta.ditto.match.infrastructure.redis.MatchingBitmapService;
import com.sparta.ditto.match.infrastructure.redis.MatchingLockService;
import com.sparta.ditto.match.infrastructure.redis.MatchingStatsService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchingHistoryRepository matchingHistoryRepository;
    private final MatchingBitmapService matchingBitmapService;
    private final MatchingLockService matchingLockService;
    private final MatchCacheService matchCacheService;
    private final MatchingStatsService matchingStatsService;
    private final EmbeddingServiceClient embeddingServiceClient;
    private final UserServiceClient userServiceClient;
    private final CosineSimilarityCalculator cosineSimilarityCalculator;
    private final MatchExplanationService matchExplanationService;
    private final HybridCandidateSearchService hybridCandidateSearchService;

    @Transactional
    public MatchResponseDto createMatch(UUID userId, MatchRequestDto request) {

        if (matchingBitmapService.hasMatchedToday(userId)) {
            throw new BusinessException(MatchErrorCode.ALREADY_MATCHED_TODAY);
        }

        if (!matchingLockService.acquireLock(userId)) {
            throw new BusinessException(MatchErrorCode.ALREADY_MATCHING);
        }

        try {
            matchingStatsService.addMatchingUser(userId);

            // 1. 내 프로필 벡터 가져오기
            UserProfileEmbeddingDto myProfile;
            try {
                myProfile = embeddingServiceClient.getUserProfile(userId).getData();
            } catch (Exception e) {
                throw new BusinessException(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE);
            }

            float[] myVector = myProfile.todayVector() != null
                    ? myProfile.todayVector()
                    : myProfile.profileVector();

            Set<String> myTags = matchCacheService.getUserTags(userId);
            if (myTags == null) {
                myTags = Set.of();
            }

            // 2. 위치 필터 활성화 시 내 동네 조회
            String neighborhood = null;
            if (Boolean.TRUE.equals(request.locationFilterOn())) {
                try {
                    UserNeighborhoodDto neighborhoodDto = userServiceClient
                            .getMyProfile(userId).getData();
                    neighborhood = neighborhoodDto != null ? neighborhoodDto.neighborhood() : null;
                } catch (Exception e) {
                    log.warn("[Match] 동네 정보 조회 실패, 위치 필터 미적용 userId={}", userId);
                }
            }

            // 3. HNSW 검색 시도 (성별/나이/동네 필터 포함)
            LinkedHashMap<UUID, Float> hnswResults = hybridCandidateSearchService.searchCandidates(
                    userId, myVector, request.genderFilter(), request.minAge(),
                    request.maxAge(), neighborhood, 50);
            UUID bestMatchId = null;
            float bestSimilarityScore = 0f;
            float bestFinalScore = -1f;
            Set<String> bestMatchTags = new HashSet<>();

            if (!hnswResults.isEmpty()) {
                // 4A. HNSW 결과가 있으면 → 태그 스코어링만 추가
                for (Map.Entry<UUID, Float> entry : hnswResults.entrySet()) {
                    UUID candidateId = entry.getKey();
                    float cosineScore = entry.getValue();

                    Set<String> candidateTags = matchCacheService.getUserTags(candidateId);
                    if (candidateTags == null) {
                        candidateTags = Set.of();
                    }

                    float tagScore = calculateTagSimilarity(myTags, candidateTags);
                    float finalScore = cosineScore * 0.5f + tagScore * 0.5f;

                    if (finalScore > bestFinalScore) {
                        bestFinalScore = finalScore;
                        bestSimilarityScore = cosineScore;
                        bestMatchId = candidateId;
                        bestMatchTags = candidateTags;
                    }
                }
            } else {
                // 4B. Fallback: 기존 Feign 방식
                Set<UUID> excludeIds = hybridCandidateSearchService.buildExcludeIds(userId);

                ActiveUserIdsDto activeIds;
                try {
                    activeIds = embeddingServiceClient.getActiveUserIds().getData();
                } catch (Exception e) {
                    throw new BusinessException(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE);
                }

                List<UUID> candidateIds = activeIds.userIds().stream()
                        .filter(id -> !id.equals(userId))
                        .filter(id -> !excludeIds.contains(id))
                        .limit(100)
                        .toList();

                if (candidateIds.isEmpty()) {
                    throw new BusinessException(MatchErrorCode.NO_MATCHING_CANDIDATE);
                }

                ProfileBatchResponseDto batchResponse;
                try {
                    batchResponse = embeddingServiceClient
                            .getProfilesBatch(new ProfileBatchRequestDto(candidateIds)).getData();
                } catch (Exception e) {
                    throw new BusinessException(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE);
                }

                for (UserProfileEmbeddingDto candidate : batchResponse.profiles()) {
                    float[] candidateVector = candidate.todayVector() != null
                            ? candidate.todayVector()
                            : candidate.profileVector();

                    float cosineScore = cosineSimilarityCalculator
                            .calculate(myVector, candidateVector);

                    Set<String> candidateTags = matchCacheService.getUserTags(candidate.userId());
                    if (candidateTags == null) {
                        candidateTags = Set.of();
                    }

                    float tagScore = calculateTagSimilarity(myTags, candidateTags);
                    float finalScore = cosineScore * 0.5f + tagScore * 0.5f;

                    if (finalScore > bestFinalScore) {
                        bestFinalScore = finalScore;
                        bestSimilarityScore = cosineScore;
                        bestMatchId = candidate.userId();
                        bestMatchTags = candidateTags;
                    }
                }
            }

            // 4. 매칭 확정
            if (bestMatchId == null) {
                throw new BusinessException(MatchErrorCode.NO_MATCHING_CANDIDATE);
            }

            List<String> commonTags = new ArrayList<>(myTags);
            commonTags.retainAll(bestMatchTags);

            String explanation;
            try {
                explanation = matchExplanationService.generateExplanation(
                        userId, bestMatchId, commonTags, bestFinalScore);
            } catch (Exception e) {
                throw new BusinessException(MatchErrorCode.EXPLANATION_GENERATION_FAILED);
            }

            MatchingHistory history = MatchingHistory.of(
                    userId, bestMatchId, bestSimilarityScore, bestFinalScore,
                    request.genderFilter(), request.locationFilterOn()
            );

            MatchingHistory saved = matchingHistoryRepository.save(history);
            MatchResponseDto response = saved.toDto(explanation);

            matchCacheService.cacheMatchResult(userId, response);
            matchingBitmapService.markAsMatched(userId);

            return response;

        } finally {
            matchingLockService.releaseLock(userId);
        }
    }

    @Transactional(readOnly = true)
    public MatchResponseDto getTodayMatch(UUID userId) {
        return matchingHistoryRepository
                .findTodayMatchByUserId(userId, LocalDate.now())
                .map(m -> m.toDto(null))
                .orElseThrow(() -> new BusinessException(MatchErrorCode.MATCH_NOT_FOUND));
    }

    @Transactional
    public void updateMatchStatus(UUID userId, UUID matchId, MatchStatusRequestDto request) {
        MatchingHistory history = matchingHistoryRepository
                .findById(matchId)
                .orElseThrow(() -> new BusinessException(MatchErrorCode.MATCH_NOT_FOUND));

        if (request.status() == MatchStatus.ACCEPTED) {
            history.accept();
        } else if (request.status() == MatchStatus.REJECTED) {
            history.reject();
        }

        matchingHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponseDto> getRecommendations(UUID userId, int limit) {
        Set<String> candidates = matchCacheService.getTopCandidates(userId, limit);

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .map(candidateId -> new RecommendationResponseDto(
                        UUID.fromString(candidateId), null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponseDto> getMatchHistory(UUID userId) {
        return matchingHistoryRepository.findAllByUserId(userId)
                .stream()
                .map(m -> m.toDto(null))
                .toList();
    }

    @Transactional(readOnly = true)
    public String getExplanation(UUID userId, UUID matchId) {
        MatchingHistory history = matchingHistoryRepository.findById(matchId)
                .orElseThrow(() -> new BusinessException(MatchErrorCode.MATCH_NOT_FOUND));

        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(MatchErrorCode.MATCH_NOT_FOUND);
        }

        Set<String> myTags = matchCacheService.getUserTags(userId);
        Set<String> matchedTags = matchCacheService.getUserTags(history.getMatchedUserId());
        if (myTags == null) {
            myTags = Set.of();
        }
        if (matchedTags == null) {
            matchedTags = Set.of();
        }

        List<String> commonTags = new ArrayList<>(myTags);
        commonTags.retainAll(matchedTags);

        try {
            return matchExplanationService.generateExplanation(
                    userId, history.getMatchedUserId(), commonTags, history.getFinalScore());
        } catch (Exception e) {
            throw new BusinessException(MatchErrorCode.EXPLANATION_GENERATION_FAILED);
        }
    }

    private float calculateTagSimilarity(Set<String> tagsA, Set<String> tagsB) {
        if (tagsA == null || tagsB == null) {
            return 0f;
        }
        if (tagsA.isEmpty() && tagsB.isEmpty()) {
            return 0f;
        }
        Set<String> intersection = new HashSet<>(tagsA);
        intersection.retainAll(tagsB);
        Set<String> union = new HashSet<>(tagsA);
        union.addAll(tagsB);
        return union.isEmpty() ? 0f : (float) intersection.size() / union.size();
    }
}
