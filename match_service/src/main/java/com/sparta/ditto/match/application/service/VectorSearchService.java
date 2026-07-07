package com.sparta.ditto.match.application.service;

import com.sparta.ditto.match.domain.repository.SyncedProfileEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * HNSW 인덱스 기반 벡터 검색 서비스
 *
 * CQRS Read Model(synced_profile_embeddings)에서
 * pgvector HNSW 인덱스를 활용한 ANN 검색 수행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final SyncedProfileEmbeddingRepository syncedRepository;

    /**
     * HNSW 검색으로 유사 유저 top-K 반환
     *
     * @param userId      검색 요청 유저 (본인 제외)
     * @param queryVector 검색 벡터
     * @param excludeIds  제외할 유저 ID (팔로잉, 차단)
     * @param topK        상위 K개
     * @return candidateId → similarity score (유사도 높은 순)
     */
    @Transactional(readOnly = true)
    public LinkedHashMap<UUID, Float> searchSimilarUsers(
            UUID userId,
            float[] queryVector,
            Set<UUID> excludeIds,
            int topK
    ) {
        String vectorStr = floatArrayToVectorString(queryVector);
        List<UUID> excludeList = safeExcludeList(excludeIds);

        List<Object[]> results = syncedRepository.findSimilarUsers(
                userId, vectorStr, excludeList, topK);

        return parseResults(results);
    }

    /**
     * 성별 필터 포함 HNSW 검색
     */
    @Transactional(readOnly = true)
    public LinkedHashMap<UUID, Float> searchWithGenderFilter(
            UUID userId,
            float[] queryVector,
            Set<UUID> excludeIds,
            String gender,
            int topK
    ) {
        String vectorStr = floatArrayToVectorString(queryVector);
        List<UUID> excludeList = safeExcludeList(excludeIds);

        List<Object[]> results = syncedRepository.findSimilarUsersWithGenderFilter(
                userId, vectorStr, excludeList, gender, topK);

        return parseResults(results);
    }

    /**
     * 성별 + 나이 필터 포함 HNSW 검색
     */
    @Transactional(readOnly = true)
    public LinkedHashMap<UUID, Float> searchWithAllFilters(
            UUID userId,
            float[] queryVector,
            Set<UUID> excludeIds,
            String gender,
            Integer minAge,
            Integer maxAge,
            int topK
    ) {
        String vectorStr = floatArrayToVectorString(queryVector);
        List<UUID> excludeList = safeExcludeList(excludeIds);

        List<Object[]> results = syncedRepository.findSimilarUsersWithFilters(
                userId, vectorStr, excludeList, gender, minAge, maxAge, topK);

        return parseResults(results);
    }

    /**
     * 동기화된 벡터 데이터 존재 여부 확인
     */
    public boolean hasSyncedData() {
        return syncedRepository.countByActiveTrue() > 0;
    }

    // excludeIds가 비어있으면 더미 UUID (IN 절 빈 리스트 방지)
    private List<UUID> safeExcludeList(Set<UUID> excludeIds) {
        return excludeIds.isEmpty()
                ? List.of(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                : new ArrayList<>(excludeIds);
    }

    // 결과 파싱: Object[] → LinkedHashMap (순서 유지)
    private LinkedHashMap<UUID, Float> parseResults(List<Object[]> results) {
        LinkedHashMap<UUID, Float> map = new LinkedHashMap<>();
        for (Object[] row : results) {
            UUID candidateId = (UUID) row[0];
            float similarity = ((Number) row[1]).floatValue();
            map.put(candidateId, similarity);
        }
        return map;
    }

    // float[] → "[0.1,0.2,0.3]" pgvector 형식
    private String floatArrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}