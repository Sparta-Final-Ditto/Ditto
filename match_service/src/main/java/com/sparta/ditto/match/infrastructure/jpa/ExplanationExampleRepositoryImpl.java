package com.sparta.ditto.match.infrastructure.jpa;

import com.sparta.ditto.match.domain.entity.ExplanationExample;
import com.sparta.ditto.match.domain.repository.ExplanationExampleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExplanationExampleRepositoryImpl
        extends ExplanationExampleRepository, JpaRepository<ExplanationExample, UUID> {

    /**
     * HNSW 인덱스 활용 ANN 검색
     * <=> 연산자: pgvector 코사인 거리 (거리 작을수록 유사) → 1 - distance = 유사도
     */
    @Override
    @Query(value = """
            SELECT id, content, 1 - (vector <=> CAST(:queryVector AS vector)) AS similarity
            FROM explanation_examples
            WHERE 1 - (vector <=> CAST(:queryVector AS vector)) >= :similarityThreshold
            ORDER BY vector <=> CAST(:queryVector AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarExamples(
            @Param("queryVector") String queryVector,
            @Param("similarityThreshold") double similarityThreshold,
            @Param("topK") int topK
    );
}
