package com.sparta.ditto.match.domain.repository;

import com.sparta.ditto.match.domain.entity.ExplanationExample;

import java.util.List;

/**
 * 매칭 설명 예시 Repository (도메인 인터페이스)
 */
public interface ExplanationExampleRepository {

    ExplanationExample save(ExplanationExample entity);

    /**
     * HNSW 벡터 검색 - 코사인 유사도 기준 topK
     * @return [id, content, similarity] 형태의 row 목록 (유사도 높은 순)
     */
    List<Object[]> findSimilarExamples(String queryVector, double similarityThreshold, int topK);
}
