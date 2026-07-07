package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.MatchingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchingHistoryJpaRepository extends JpaRepository<MatchingHistory, UUID> {

    // 오늘 매칭 이력 조회
    @Query("SELECT m FROM MatchingHistory m WHERE m.userId = :userId AND m.matchedAt >= :startOfDay")
    Optional<MatchingHistory> findTodayMatch(
            @Param("userId") UUID userId,
            @Param("startOfDay") Instant startOfDay
    );

    List<MatchingHistory> findAllByUserId(UUID userId);
}