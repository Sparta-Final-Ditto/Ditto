package com.sparta.ditto.match.application.service;


import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.exception.MatchErrorCode;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import com.sparta.ditto.match.infrastructure.redis.MatchCacheService;
import com.sparta.ditto.match.infrastructure.redis.MatchingBitmapService;
import com.sparta.ditto.match.infrastructure.redis.MatchingLockService;
import com.sparta.ditto.match.infrastructure.redis.MatchingStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;         // ← 이거 추가
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchingHistoryRepository matchingHistoryRepository;
    private final MatchingBitmapService matchingBitmapService;
    private final MatchingLockService matchingLockService;
    private final MatchCacheService matchCacheService;
    private final MatchingStatsService matchingStatsService;


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

            // 4. Sorted Set에서 배치 점수 기반 후보 조회
            Set<String> candidates = matchCacheService.getTopCandidates(userId, 10);

            // 5. TODO: pgvector 코사인 유사도 계산

            // 6. 태그, 시간대 Redis에서 조회
            Set<String> userTags = matchCacheService.getUserTags(userId);
            String userTimeSlot = matchCacheService.getUserTimeSlot(userId);

            // 7. TODO: 가중치 스코어링

            // 8. 매칭 이력 저장
            MatchingHistory history = MatchingHistory.of(
                    userId,
                    UUID.randomUUID(),
                    0.0f,
                    0.0f,
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
                    saved.getStatus()
            );

            // 9. 매칭 결과 캐싱
            matchCacheService.cacheMatchResult(userId, response);

            // 10. Bitmap에 오늘 매칭 완료 표시
            matchingBitmapService.markAsMatched(userId);

            return response;

        } finally {
            matchingLockService.releaseLock(userId);
        }
    }

    @Transactional(readOnly = true)
    public MatchResponseDto getTodayMatch(UUID userId) {
        return matchingHistoryRepository.findTodayMatchByUserId(userId, LocalDate.now())
                .map(m -> new MatchResponseDto(
                        m.getId(),
                        m.getMatchedUserId(),
                        m.getSimilarityScore(),
                        m.getFinalScore(),
                        m.getMatchedAt(),
                        m.getStatus()
                ))
                .orElseThrow(() -> new BusinessException(MatchErrorCode.MATCH_NOT_FOUND));
    }
}