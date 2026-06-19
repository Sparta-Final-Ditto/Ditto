package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchingHistoryRepositoryImpl implements MatchingHistoryRepository {

    private final MatchingHistoryJpaRepository jpaRepository;

    @Override
    public MatchingHistory save(MatchingHistory matchingHistory) {
        return jpaRepository.save(matchingHistory);
    }

    @Override
    public Optional<MatchingHistory> findTodayMatchByUserId(UUID userId, LocalDate today) {
        // 오늘 0시 기준으로 조회
        LocalDateTime startOfDay = today.atStartOfDay();
        return jpaRepository.findTodayMatch(userId, startOfDay);
    }

    @Override
    public List<MatchingHistory> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId);
    }

    @Override
    public Optional<MatchingHistory> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}