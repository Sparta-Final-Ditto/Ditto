package com.sparta.ditto.match.domain.repository;

import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import java.util.Optional;
import java.util.UUID;

public interface MatchingExplanationRepository {

    // RAG 매칭 설명 저장
    MatchingExplanation save(MatchingExplanation matchingExplanation);

    // 매칭 ID로 설명 조회
    Optional<MatchingExplanation> findByUserIdAndMatchedUserId(UUID userId, UUID matchedUserId);
}