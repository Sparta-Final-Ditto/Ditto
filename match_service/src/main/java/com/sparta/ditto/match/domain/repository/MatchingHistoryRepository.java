package com.sparta.ditto.match.domain.repository;

import com.sparta.ditto.match.domain.entity.MatchingHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingHistoryRepository {

    // 매칭 이력 저장
    MatchingHistory save(MatchingHistory matchingHistory);

    // 오늘 매칭 이력 조회 (하루 1회 제한 체크용)
    Optional<MatchingHistory> findTodayMatchByUserId(UUID userId, LocalDate today);

    // 전체 매칭 이력 조회
    List<MatchingHistory> findAllByUserId(UUID userId);

    // 매칭 ID로 조회
    Optional<MatchingHistory> findById(UUID id);

}