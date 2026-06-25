package com.sparta.ditto.match.application.service;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.match.application.dto.*;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

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

    @Transactional
    public MatchResponseDto createMatch(UUID userId, MatchRequestDto request) {

        // 1. Bitmap으로 빠르게 하루 1회 제한 체크
        if (matchingBitmapService.hasMatchedToday(userId)) {
            throw new BusinessException(MatchErrorCode.ALREADY_MATCHED_TODAY);
        }

        // 2. 분산 락 획득
        if (!matchingLockService.acquireLock(userId)) {
            throw new BusinessException(MatchErrorCode.ALREADY_MATCHING);
        }

        try {
            // 3. HyperLogLog 통계 기록
            matchingStatsService.addMatchingUser(userId);

            // 4. 내 벡터 가져오기
            UserProfileEmbeddingDto myProfile;
            try {
                myProfile = embeddingServiceClient.getUserProfile(userId).getData();
            } catch (Exception e) {
                throw new BusinessException(MatchErrorCode.EMBEDDING_SERVICE_UNAVAILABLE);
            }

            float[] myVector = myProfile.todayVector() != null
                    ? myProfile.todayVector()
                    : myProfile.profileVector();

            // 5. 팔로잉 + 차단 유저 제외 Set 구성
            Set<UUID> excludeIds = buildExcludeIds(userId);

            // 6. active 유저 후보군 배치로 벡터 가져오기
            // TODO: [고도화] HNSW ANN 검색으로 교체 예정
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

            // 7. 태그 Redis에서 조회
            Set<String> myTags = matchCacheService.getUserTags(userId);

            // 8. 코사인 유사도 + 태그 가중치 스코어링
            UUID bestMatchId = null;
            float bestSimilarityScore = 0f;
            float bestFinalScore = -1f;
            Set<String> bestMatchTags = new HashSet<>();

            for (UserProfileEmbeddingDto candidate : batchResponse.profiles()) {
                float[] candidateVector = candidate.todayVector() != null
                        ? candidate.todayVector()
                        : candidate.profileVector();

                float cosineScore = cosineSimilarityCalculator.calculate(myVector, candidateVector);
                Set<String> candidateTags = matchCacheService.getUserTags(candidate.userId());
                float tagScore = calculateTagSimilarity(myTags, candidateTags);
                float finalScore = cosineScore * 0.5f + tagScore * 0.5f;

                log.debug("[Match] candidateId={} cosine={} tag={} final={}",
                        candidate.userId(), cosineScore, tagScore, finalScore);

                if (finalScore > bestFinalScore) {
                    bestFinalScore = finalScore;
                    bestSimilarityScore = cosineScore;
                    bestMatchId = candidate.userId();
                    bestMatchTags = candidateTags;
                }
            }

            if (bestMatchId == null) {
                throw new BusinessException(MatchErrorCode.NO_MATCHING_CANDIDATE);
            }

            // 9. 공통 태그 추출
            List<String> commonTags = new ArrayList<>(myTags);
            commonTags.retainAll(bestMatchTags);

            // 10. RAG 매칭 설명 생성
            String explanation;
            try {
                explanation = matchExplanationService.generateExplanation(
                        userId, bestMatchId, commonTags, bestFinalScore);
            } catch (Exception e) {
                throw new BusinessException(MatchErrorCode.EXPLANATION_GENERATION_FAILED);
            }

            // 11. 매칭 이력 저장
            MatchingHistory history = MatchingHistory.of(
                    userId,
                    bestMatchId,
                    bestSimilarityScore,
                    bestFinalScore,
                    request.genderFilter(),
                    request.locationFilterOn()
            );

            MatchingHistory saved = matchingHistoryRepository.save(history);

            MatchResponseDto response = new MatchResponseDto(
                    saved.getId(),
                    saved.getMatchedUserId(),
                    saved.getSimilarityScore(),
                    saved.getFinalScore(),
                    saved.getMatchedAt(),
                    saved.getStatus(),
                    explanation
            );

            // 12. 매칭 결과 캐싱
            matchCacheService.cacheMatchResult(userId, response);

            // 13. Bitmap에 오늘 매칭 완료 표시
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
                .map(m -> new MatchResponseDto(
                        m.getId(),
                        m.getMatchedUserId(),
                        m.getSimilarityScore(),
                        m.getFinalScore(),
                        m.getMatchedAt(),
                        m.getStatus(),
                        null
                ))
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
                        UUID.fromString(candidateId),
                        null
                ))
                .toList();
    }

    // 매칭 이력 전체 조회
    @Transactional(readOnly = true)
    public List<MatchResponseDto> getMatchHistory(UUID userId) {
        return matchingHistoryRepository.findAllByUserId(userId)
                .stream()
                .map(m -> new MatchResponseDto(
                        m.getId(),
                        m.getMatchedUserId(),
                        m.getSimilarityScore(),
                        m.getFinalScore(),
                        m.getMatchedAt(),
                        m.getStatus(),
                        null
                ))
                .toList();
    }

    // 매칭 설명 조회 (RAG)
    @Transactional(readOnly = true)
    public String getExplanation(UUID userId, UUID matchId) {
        MatchingHistory history = matchingHistoryRepository.findById(matchId)
                .orElseThrow(() -> new BusinessException(MatchErrorCode.MATCH_NOT_FOUND));

        // 내 매칭인지 검증
        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(MatchErrorCode.MATCH_NOT_FOUND);
        }

        // Redis 캐시에서 공통 태그 조회
        Set<String> myTags = matchCacheService.getUserTags(userId);
        Set<String> matchedTags = matchCacheService.getUserTags(history.getMatchedUserId());
        List<String> commonTags = new ArrayList<>(myTags);
        commonTags.retainAll(matchedTags);

        try {
            return matchExplanationService.generateExplanation(
                    userId,
                    history.getMatchedUserId(),
                    commonTags,
                    history.getFinalScore()
            );
        } catch (Exception e) {
            throw new BusinessException(MatchErrorCode.EXPLANATION_GENERATION_FAILED);
        }
    }

    private Set<UUID> buildExcludeIds(UUID userId) {
        Set<UUID> excludeIds = new HashSet<>();

        try {
            List<UserPublicProfileDto> followings = userServiceClient
                    .getFollowings(userId).getData();
            followings.forEach(f -> excludeIds.add(f.id()));
        } catch (Exception e) {
            throw new BusinessException(MatchErrorCode.USER_SERVICE_UNAVAILABLE);
        }

        try {
            List<UserPublicProfileDto> blockedUsers = userServiceClient
                    .getBlockedUsers(userId).getData();
            blockedUsers.forEach(b -> excludeIds.add(b.id()));
        } catch (Exception e) {
            throw new BusinessException(MatchErrorCode.USER_SERVICE_UNAVAILABLE);
        }

        return excludeIds;
    }

    private float calculateTagSimilarity(Set<String> tagsA, Set<String> tagsB) {
        if (tagsA.isEmpty() && tagsB.isEmpty()) return 0f;
        Set<String> intersection = new HashSet<>(tagsA);
        intersection.retainAll(tagsB);
        Set<String> union = new HashSet<>(tagsA);
        union.addAll(tagsB);
        return union.isEmpty() ? 0f : (float) intersection.size() / union.size();
    }
}