package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import com.sparta.ditto.match.domain.repository.MatchingExplanationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchingExplanationRepositoryImpl implements MatchingExplanationRepository {

    private final MatchingExplanationJpaRepository jpaRepository;

    @Override
    public MatchingExplanation save(MatchingExplanation matchingExplanation) {
        return jpaRepository.save(matchingExplanation);
    }

    @Override
    public Optional<MatchingExplanation> findByUserIdAndMatchedUserId(UUID userId, UUID matchedUserId) {
        return jpaRepository.findByUserIdAndMatchedUserId(userId, matchedUserId);
    }
}