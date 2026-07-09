package com.sparta.ditto.match.domain.repository;

import com.sparta.ditto.match.domain.entity.SyncedProfileEmbedding;

import java.util.*;

/**
 * 동기화된 프로필 임베딩 Repository (도메인 인터페이스)
 */
public interface SyncedProfileEmbeddingRepository {

    Optional<SyncedProfileEmbedding> findById(UUID userId);

    SyncedProfileEmbedding save(SyncedProfileEmbedding entity);

    long countByActiveTrue();

    // HNSW 벡터 검색 - 기본
    List<Object[]> findSimilarUsers(UUID userId, String queryVector, List<UUID> excludeIds, int topK);

    // HNSW 벡터 검색 - 성별 필터
    List<Object[]> findSimilarUsersWithGenderFilter(
            UUID userId, String queryVector, List<UUID> excludeIds, String gender, int topK);

    // HNSW 벡터 검색 - 성별 + 나이 + 동네 필터
    List<Object[]> findSimilarUsersWithFilters(
            UUID userId, String queryVector, List<UUID> excludeIds,
            String gender, Integer minAge, Integer maxAge, String neighborhood, int topK);
}