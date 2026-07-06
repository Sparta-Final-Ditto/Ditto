package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.SyncedProfileEmbedding;
import com.sparta.ditto.match.domain.repository.SyncedProfileEmbeddingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface SyncedProfileEmbeddingRepositoryImpl
        extends SyncedProfileEmbeddingRepository, JpaRepository<SyncedProfileEmbedding, UUID> {

    /**
     * HNSW 인덱스 활용 ANN 검색 (기본)
     * <=> 연산자: pgvector 코사인 거리 (거리 작을수록 유사)
     */
    @Override
    @Query(value = """
            SELECT user_id, 1 - (vector <=> CAST(:queryVector AS vector)) AS similarity
            FROM synced_profile_embeddings
            WHERE active = true
              AND user_id != :userId
              AND user_id NOT IN (:excludeIds)
            ORDER BY vector <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarUsers(
            @Param("userId") UUID userId,
            @Param("queryVector") String queryVector,
            @Param("excludeIds") List<UUID> excludeIds,
            @Param("topK") int topK
    );

    /**
     * 성별 필터 포함 HNSW 검색
     */
    @Override
    @Query(value = """
            SELECT user_id, 1 - (vector <=> CAST(:queryVector AS vector)) AS similarity
            FROM synced_profile_embeddings
            WHERE active = true
              AND user_id != :userId
              AND user_id NOT IN (:excludeIds)
              AND (:gender IS NULL OR gender = :gender)
            ORDER BY vector <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarUsersWithGenderFilter(
            @Param("userId") UUID userId,
            @Param("queryVector") String queryVector,
            @Param("excludeIds") List<UUID> excludeIds,
            @Param("gender") String gender,
            @Param("topK") int topK
    );

    /**
     * 성별 + 나이 필터 포함 HNSW 검색
     */
    @Override
    @Query(value = """
            SELECT user_id, 1 - (vector <=> CAST(:queryVector AS vector)) AS similarity
            FROM synced_profile_embeddings
            WHERE active = true
              AND user_id != :userId
              AND user_id NOT IN (:excludeIds)
              AND (:gender IS NULL OR gender = :gender)
              AND (:minAge IS NULL OR EXTRACT(YEAR FROM AGE(birthdate)) >= :minAge)
              AND (:maxAge IS NULL OR EXTRACT(YEAR FROM AGE(birthdate)) <= :maxAge)
            ORDER BY vector <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarUsersWithFilters(
            @Param("userId") UUID userId,
            @Param("queryVector") String queryVector,
            @Param("excludeIds") List<UUID> excludeIds,
            @Param("gender") String gender,
            @Param("minAge") Integer minAge,
            @Param("maxAge") Integer maxAge,
            @Param("topK") int topK
    );
}