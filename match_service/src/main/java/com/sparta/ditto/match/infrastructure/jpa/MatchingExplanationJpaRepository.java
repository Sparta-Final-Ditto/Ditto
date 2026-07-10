package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchingExplanationJpaRepository extends JpaRepository<MatchingExplanation, UUID> {
    Optional<MatchingExplanation> findByUserIdAndMatchedUserId(UUID userId, UUID matchedUserId);
}
