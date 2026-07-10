package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.MatchingHistory;
import com.sparta.ditto.match.domain.repository.MatchingHistoryRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
        Instant startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant();
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
