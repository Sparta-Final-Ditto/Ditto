package com.sparta.ditto.match.application.service;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.match.application.dto.MatchRequestDto;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import com.sparta.ditto.match.application.exception.MatchErrorCode;
import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchingHistoryRepository matchingHistoryRepository;

    @Transactional
    public MatchResponseDto createMatch(UUID userId, MatchRequestDto request) {

        // 1. 하루 1회 제한 체크
        matchingHistoryRepository.findTodayMatchByUserId(userId, LocalDate.now())
                .ifPresent(m -> {
                    throw new BusinessException(MatchErrorCode.ALREADY_MATCHED_TODAY);
                });

        // 2. TODO: OpenFeign으로 embedding_service에서 active 유저 목록 조회

        // 3. TODO: pgvector 코사인 유사도 계산

        // 4. TODO: 가중치 스코어링 (텍스트 60% + 태그 20% + 시간대 20%)

        // 5. TODO: 필터링 (성별, 위치, 나이대)

        // 6. 매칭 이력 저장
        MatchingHistory history = MatchingHistory.of(
                userId,
                UUID.randomUUID(), // TODO: 실제 매칭된 유저 ID로 교체
                0.0f,              // TODO: 실제 유사도로 교체
                0.0f,              // TODO: 실제 final_score로 교체
                request.genderFilter(),
                request.locationFilterOn()
        );

        MatchingHistory saved = matchingHistoryRepository.save(history);

        return new MatchResponseDto(
                saved.getId(),
                saved.getMatchedUserId(),
                saved.getSimilarityScore(),
                saved.getFinalScore(),
                saved.getMatchedAt(),
                saved.getStatus()
        );
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